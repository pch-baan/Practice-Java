# Flow: Tạo User — POST /api/v1/users

Version hiện tại đang chạy: **V3** (`CreateUserUseCaseImplV3` — duy nhất có `@Service`)

---

## Tầng 1 — API (api-portal)

```
HTTP POST /api/v1/users
  { username, email, password }
        │
        ▼
UserController.createUser()
  @Valid kiểm tra:
    - username: không được trống, 3-50 ký tự
    - email: không được trống, đúng định dạng email
    - password: không được trống, tối thiểu 8 ký tự
        │
        ▼
UserApiMapper.toCommand()
  CreateUserRequest → CreateUserCommandDto
  (chỉ copy String fields, không có logic)
        │
        ▼
createUserUseCase.execute(command)
        │  (gọi qua interface ICreateUserUseCase — port)
        ▼
[nhận về: UserResponseDto]
        │
UserApiMapper.toResponse()
  UserResponseDto → UserResponse
  (map role/status enum domain sang enum HTTP riêng)
        │
        ▼
ResponseEntity 201 CREATED
```

---

## Tầng 2 — Application (CreateUserUseCaseImplV3)

```
execute(CreateUserCommandDto)
        │
        ├─ EmailVO.of(email)        → kiểm tra + chuẩn hóa (trim, lowercase, regex)
        ├─ UsernameVO.of(username)  → kiểm tra (3-50 ký tự, chỉ chứa ký tự hợp lệ)
        │
        ├─ passwordEncoder.encode(password)
        │    └─ BCrypt ~300ms
        │    └─ NGOÀI transaction (không chiếm DB connection)
        │
        └─ TransactionTemplate.execute()  ← mở transaction TẠI ĐÂY
               │
               ├─ userRepository.save(User.create(...))
               │
               └─ userProfileRepository.save(UserProfile.createEmpty(...))
```

### Tại sao V3 quan trọng?

| Version | Vấn đề | Giới hạn đồng thời |
|---------|--------|-------------------|
| V1 | BCrypt nằm *trong* `@Transactional` → giữ connection 330ms | Lỗi khi > 160 request |
| V2 | BCrypt ngoài transaction, nhưng còn 2 SELECT pre-check thừa | Lỗi khi > 1.670 request |
| V3 | Bỏ hẳn pre-check, chỉ 2 INSERT/user, dựa vào DB constraint | Tối ưu cho tải cao |

---

## Tầng 3 — Domain

```
User.create(username, email, passwordHash)
  └─ new User(
       UUID.randomUUID(),       // tạo ID mới
       username,                // UsernameVO
       email,                   // EmailVO
       PasswordHashVO.of(hash), // bọc passwordHash vào Value Object
       role = USER,             // mặc định
       status = ACTIVE,         // mặc định
       createdAt = now(),
       updatedAt = now()
     )

UserProfile.createEmpty(userId)
  └─ tạo profile trống, liên kết theo userId
```

---

## Tầng 4 — Infrastructure (UserPostgresqlAdapter)

```
userRepository.save(user)
        │
        ├─ mapper.toJpaEntity(user)          → domain → JPA entity
        ├─ userJpaRepository.saveAndFlush()  → INSERT + flush ngay lập tức
        │    └─ nếu email/username bị trùng:
        │         DataIntegrityViolationException
        │              └─ catch → UserConflictException
        │                         (DB constraint là nguồn sự thật)
        └─ mapper.toDomain(savedEntity)      → JPA → domain (trả về)
```

> `saveAndFlush()` thay vì `save()`: flush ngay trong transaction để bắt constraint violation
> tại adapter, convert sang `UserConflictException` trước khi nổi lên. Tầng trên không bao
> giờ thấy `DataIntegrityViolationException`.

---

## Toàn bộ chuỗi biến đổi object

```
HTTP JSON
  → CreateUserRequest           (api-portal, @Valid)
  → CreateUserCommandDto        (UserApiMapper.toCommand)
  → EmailVO / UsernameVO        (Value Objects, kiểm tra tại domain)
  → User (domain model)         (User.create — factory method)
  → UserJpaEntity               (UserPersistenceMapper.toJpaEntity)
  → [DB INSERT]
  → UserJpaEntity (đã lưu)
  → User (domain)               (UserPersistenceMapper.toDomain)
  → UserResponseDto             (UserResponseDto.from)
  → UserResponse                (UserApiMapper.toResponse)
  → HTTP 201 JSON
```

---

## Điểm nổi bật về kiến trúc

- **Domain thuần túy** — `User`, `EmailVO`, `UsernameVO` không biết Spring hay JPA
- **Port/Adapter** — `IUserRepository` (domain port) ↔ `UserPostgresqlAdapter` (infrastructure adapter)
- **Transaction boundary nhỏ nhất** — BCrypt nằm ngoài, chỉ 2 INSERT trong transaction
- **Exception translation tại adapter** — tầng trên không bao giờ thấy `DataIntegrityViolationException`
