# Bug: `save()` vs `saveAndFlush()` — Tại sao `catch (DataIntegrityViolationException)` không hoạt động?

## Vấn đề phát hiện

`UserPostgresqlAdapter.save()` có đoạn code sau để bắt concurrent duplicate:

```java
public User save(User user) {
    try {
        UserJpaEntity entity      = mapper.toJpaEntity(user);
        UserJpaEntity savedEntity = userJpaRepository.save(entity); // ← BUG ở đây
        return mapper.toDomain(savedEntity);
    } catch (DataIntegrityViolationException ex) {
        throw new UserConflictException("User already exists (concurrent registration detected)");
    }
}
```

Khi chạy integration test concurrent (2 thread cùng tạo user cùng email), exception thực tế bắt được là:

```
DataIntegrityViolationException: duplicate key value violates unique constraint "uq_users_email"
```

Không phải `UserConflictException` như mong đợi — nghĩa là `catch` block không bao giờ được chạy.

---

## Root Cause — JPA Write-Behind (Deferred Flush)

JPA không thực thi SQL ngay khi gọi `save()`. Thay vào đó, nó **queue entity vào Persistence Context** và chỉ gửi SQL xuống DB khi **transaction commit**.

### Timeline với `save()` (BUG):

```
execute() bắt đầu → Spring mở transaction
    │
    ▼
UserPostgresqlAdapter.save() được gọi
    │
    ├─► try { bắt đầu
    │
    ├─► jpaRepo.save(entity)
    │       └─► JPA queue INSERT vào Persistence Context
    │           KHÔNG có SQL nào chạy ở đây
    │
    ├─► return domain object  ← try-catch kết thúc bình thường, không exception
    │
    └─► } catch(DataIntegrityViolationException) ← KHÔNG BAO GIỜ VÀO ĐÂY
    │
    ▼
userProfileRepository.save(profile) → tiếp tục bình thường
    │
    ▼
execute() kết thúc → Spring commit transaction
    │
    ▼
JPA flush: INSERT SQL được gửi xuống DB
    │
    ▼
DB phát hiện duplicate email → ném exception
    │
    ▼
DataIntegrityViolationException lan ra NGOÀI execute()
    └─► try-catch trong adapter đã bị thoát từ lâu → không bắt được ✘
```

---

## Fix — Dùng `saveAndFlush()`

`saveAndFlush()` = `save()` + flush ngay lập tức → SQL INSERT chạy ngay trong lời gọi hàm, không đợi commit.

```java
UserJpaEntity savedEntity = userJpaRepository.saveAndFlush(entity); // ← fix
```

### Timeline với `saveAndFlush()` (ĐÚNG):

```
execute() bắt đầu → Spring mở transaction
    │
    ▼
UserPostgresqlAdapter.save() được gọi
    │
    ├─► try { bắt đầu
    │
    ├─► jpaRepo.saveAndFlush(entity)
    │       └─► JPA flush ngay: INSERT SQL được gửi xuống DB
    │           DB phát hiện duplicate → ném DataIntegrityViolationException
    │           Exception ném ra TRONG try-catch ✔
    │
    └─► } catch(DataIntegrityViolationException ex) {
            throw new UserConflictException(...)  ← được chạy ✔
        }
```

---

## Tóm tắt

| | `save()` | `saveAndFlush()` |
|---|---|---|
| SQL INSERT chạy khi nào | Khi transaction commit | Ngay lập tức |
| `DataIntegrityViolationException` ném ra ở đâu | Ngoài `execute()` | Trong `try-catch` của adapter |
| `catch` block bắt được không | ✘ Không | ✔ Có |
| Convert sang `UserConflictException` | ✘ Không | ✔ Có |

## Bài học

> **`catch (DataIntegrityViolationException)` trong JPA adapter chỉ hoạt động đúng khi dùng `saveAndFlush()`.**
>
> Dùng `save()` (deferred flush) khiến exception xảy ra sau khi transaction commit —
> lúc đó đã thoát khỏi mọi try-catch trong code application, không thể convert được nữa.

---

## Phát hiện bằng Integration Test

Bug này được phát hiện qua `CreateUserConcurrentIntegrationTest` —
test race condition với 2 thread cùng tạo user cùng email cùng lúc.

Unit test (mock repository) không thể phát hiện bug này vì:
- Mock không có JPA flush behavior
- Mock throw exception ngay khi `save()` được gọi → vô tình che giấu bug
