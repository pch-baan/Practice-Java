# Các vấn đề thường gặp khi tạo user với lưu lượng cao

## Bức tranh tổng thể

```
CreateUserUseCaseImpl.execute()
    │
    ├─ 1. EmailVO.of()                         → CPU, nhẹ
    ├─ 2. UsernameVO.of()                      → CPU, nhẹ
    ├─ 3. validateUniqueConstraints()          → DB query (existsByEmail + existsByUsername)
    ├─ 4. passwordEncoder.encode()             → CPU nặng (~200-300ms, BCrypt)
    ├─ 5. userRepository.save()                → DB write
    ├─ 6. userProfileRepository.save()         → DB write
    └─ 7. return UserResponseDto              → nhẹ

Toàn bộ nằm trong @Transactional
→ DB connection bị GIỮ từ bước 3 đến bước 6
→ BCrypt ở bước 4 làm connection bị giữ thêm 200-300ms không cần thiết
```

---

## Vấn đề 1 — BCrypt nằm trong `@Transactional` (nghiêm trọng nhất)

### Tại sao nguy hiểm?

`@Transactional` yêu cầu Spring mở 1 DB connection từ HikariCP pool và **giữ nó suốt toàn bộ method**.

BCrypt được thiết kế **chủ ý chậm** (~200-300ms mỗi lần) để chống brute force attack.
Trong thời gian BCrypt tính toán, DB connection bị giữ nhưng không làm gì cả — lãng phí.

### Hệ quả dưới high load

```
HikariCP pool mặc định: 10 connections

Cùng 1 lúc có 10 request đang BCrypt (mỗi cái giữ 1 connection ~300ms):

  Request  1 → connection #1  → [===BCrypt 300ms===] → release
  Request  2 → connection #2  → [===BCrypt 300ms===] → release
  ...
  Request 10 → connection #10 → [===BCrypt 300ms===] → release

  Request 11 → pool rỗng → BLOCK và chờ...
  Request 12 → pool rỗng → BLOCK và chờ...
  ...
  Request 30 → chờ quá lâu →
    HikariPool: Connection is not available, request timed out after 30000ms
```

Triệu chứng trên production:
- API tạo user đột ngột chậm khi traffic tăng
- Log xuất hiện `Connection is not available, request timed out`
- CPU cao (BCrypt) nhưng DB lại idle (không làm gì trong lúc BCrypt tính)

### Fix

Tách BCrypt ra **ngoài** `@Transactional` — chỉ mở transaction khi thật sự cần write vào DB:

```java
// Trước fix: BCrypt TRONG transaction → giữ connection 300ms không cần thiết
@Transactional
public UserResponseDto execute(CreateUserCommandDto command) {
    userDomainService.validateUniqueConstraints(...);  // DB query
    String hash = passwordEncoder.encode(password);   // BCrypt 300ms ← connection bị giữ
    userRepository.save(...);                          // DB write
    userProfileRepository.save(...);                  // DB write
}

// Sau fix: BCrypt NGOÀI transaction → connection chỉ mở khi write
public UserResponseDto execute(CreateUserCommandDto command) {
    EmailVO email       = EmailVO.of(command.email());
    UsernameVO username = UsernameVO.of(command.username());

    // BCrypt tính xong trước — không cần connection
    String passwordHash = passwordEncoder.encode(command.password());

    // Chỉ mở transaction khi cần write
    return persist(email, username, passwordHash);
}

@Transactional
private UserResponseDto persist(EmailVO email, UsernameVO username, String passwordHash) {
    userDomainService.validateUniqueConstraints(email, username);
    User savedUser = userRepository.save(User.create(username, email, passwordHash));
    userProfileRepository.save(UserProfile.createEmpty(savedUser.getId()));
    return UserResponseDto.from(savedUser);
}
```

Connection chỉ bị giữ trong thời gian thực sự cần thiết (DB query + write), không bị block bởi BCrypt nữa.

---

## Vấn đề 2 — `saveAndFlush()` trade-off dưới high load

### Bối cảnh

`save()` (mặc định của JPA) dùng write-behind: queue INSERT, flush khi transaction commit.
`saveAndFlush()` flush ngay lập tức sau mỗi lời gọi.

Đã đổi sang `saveAndFlush()` trong `UserPostgresqlAdapter` để bắt `DataIntegrityViolationException`
bên trong try-catch (xem `jpa-save-vs-saveAndFlush.md`).

### Trade-off

```
save() + batch:
  1000 request → 1 round-trip DB với 1000 INSERT → nhanh

saveAndFlush():
  1000 request → 1000 round-trip DB riêng lẻ → chậm hơn
```

### Tại sao chấp nhận trade-off này?

Đây là quyết định **correctness over performance**:
- `save()` khiến `catch (DataIntegrityViolationException)` không bao giờ chạy được
- Race condition sẽ leak `DataIntegrityViolationException` (lỗi infrastructure) lên tầng trên
- Đổi `saveAndFlush()` đảm bảo adapter convert đúng sang `UserConflictException` (lỗi domain)

Nếu sau này cần optimize performance, giải pháp đúng là dùng
**DB unique constraint + accept exception at DB level** thay vì kiểm tra trước bằng `existsByEmail()`.

---

## Thứ tự ưu tiên giải quyết

| # | Vấn đề | Mức độ | Trạng thái |
|---|---|---|---|
| 1 | BCrypt nằm trong `@Transactional` | Nghiêm trọng — gây connection pool exhaustion | Chưa fix |
| 2 | `saveAndFlush()` trade-off | Chấp nhận được — correctness > performance | Đã fix (đổi từ `save()`) |
