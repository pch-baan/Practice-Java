# Chiến lược: DB Unique Constraint + Accept Exception at DB Level

## Bối cảnh

`saveAndFlush()` được dùng thay `save()` để bắt `DataIntegrityViolationException` bên trong try-catch
của adapter (xem `jpa-save-vs-saveAndFlush.md`). Nhưng cách này có trade-off về hiệu năng dưới high load.

Đây là chiến lược tối ưu hơn khi cần scale.

---

## Flow hiện tại (2 round-trips)

```
Request đến
    │
    ▼
existsByEmail()  ← SELECT (round-trip 1)
    │
    ├── exists → throw UserConflictException (sớm)
    │
    └── not exists
            │
            ▼
        saveAndFlush()  ← INSERT (round-trip 2, flush ngay)
            │
            └── DataIntegrityViolationException (race condition)
                    │
                    ▼
              catch tại Adapter → UserConflictException
```

**Vấn đề:** mỗi request tốn 2 round-trip tới DB.

---

## Chiến lược mới: "Ask Forgiveness, Not Permission" (1 round-trip)

```
Request đến
    │
    ▼
save()  ← INSERT thẳng, KHÔNG check trước
    │
    ├── transaction commit OK → done
    │
    └── ConstraintViolationException (DB unique constraint nổ)
            │
            ▼
      catch tại UseCase → UserConflictException
```

### Tại sao dùng được `save()` thay vì `saveAndFlush()`?

Với cách mới, không cần catch exception trong adapter — exception nổ tại điểm commit transaction
(tầng UseCase), nên write-behind của `save()` không còn là vấn đề.

```java
// UseCase level
@Transactional
public void createUser(CreateUserCommand cmd) {
    try {
        userPort.save(new User(cmd.email(), ...));  // write-behind, chưa flush
        // transaction commit ở đây → DB mới check unique constraint
    } catch (DataIntegrityViolationException e) {
        throw new UserConflictException(cmd.email());
    }
}
```

---

## So sánh 3 cách

| Tiêu chí              | existsByEmail() trước | saveAndFlush()    | save() + catch tại UseCase |
|-----------------------|-----------------------|-------------------|----------------------------|
| Round-trips/request   | 2 (SELECT + INSERT)   | 2                 | **1**                      |
| An toàn race condition| Không                 | Có                | **Có**                     |
| Batch-able            | Không                 | Không             | **Có**                     |
| Exception xử lý ở đâu| Adapter (sớm)         | Adapter           | UseCase                    |

---

## Bonus: tại sao batch-able?

Khi dùng `save()`, Hibernate có thể gom nhiều INSERT lại trong một round-trip:

```
1000 concurrent requests
    │
    ▼
Hibernate batch buffer: [INSERT u1, INSERT u2, ..., INSERT u1000]
    │
    ▼
1 round-trip: "INSERT INTO users VALUES (u1), (u2), ..., (u1000)"
    │
    ├── u500 conflict → exception → UseCase catch → UserConflictException
    └── phần còn lại commit OK
```

Thay vì 1000 round-trips riêng lẻ như `saveAndFlush()`.

Cần bật batch insert trong `application.yml`:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
```

---

## Tóm tắt

**"Accept exception at DB level"** = bỏ `existsByEmail()` check trước, để DB unique constraint
tự phát hiện duplicate, catch `DataIntegrityViolationException` tại tầng UseCase khi transaction commit.

Kết quả:
- Giảm từ 2 xuống 1 round-trip/request
- An toàn với race condition
- Mở đường cho Hibernate batch insert dưới high load
