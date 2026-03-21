# @Configuration, @Bean và Spring Bean Injection

## Spring IoC Container — "Kho chứa đồ"

Spring như một **nhà kho thông minh** — tạo ra, quản lý, và phân phát các object khi cần.

```
┌─────────────────────────────────┐
│        Spring IoC Container     │
│  (Kho chứa tất cả các "đồ dùng")│
│                                 │
│  [UserDomainService]            │
│  [PasswordEncoder]              │
│  [UserRepository]               │
│  [UserUseCase]                  │
│  ...                            │
└─────────────────────────────────┘
```

Mỗi "đồ dùng" trong kho gọi là **Bean** — là object được Spring tạo ra và quản lý.

---

## `@Bean` — "Công thức tạo đồ"

Thay vì tự `new` object, bạn nói với Spring: *"Khi cần `UserDomainService`, hãy tạo theo công thức này."*

```java
@Bean
public UserDomainService userDomainService(IUserRepository userRepository) {
    return new UserDomainService(userRepository);  // ← công thức
}
```

Spring đọc method này và:

```
Spring thấy @Bean
        │
        ▼
Cần IUserRepository? → Lấy từ kho ✅
        │
        ▼
new UserDomainService(userRepository)
        │
        ▼
Bỏ vào kho → [UserDomainService bean]
```

---

## `@Configuration` — "Quyển sổ công thức"

`@Bean` không thể đứng một mình — phải nằm trong class có `@Configuration`.

`@Configuration` nói với Spring: *"Class này chứa các công thức tạo bean, hãy đọc khi khởi động."*

```
Lúc Spring khởi động:
        │
        ▼
Quét toàn bộ class có @Configuration
        │
        ▼
Đọc từng method có @Bean
        │
        ▼
Tạo bean → bỏ vào kho
```

---

## Inject — "Giao hàng tự động"

Khi một class cần `UserDomainService`, Spring **tự lấy từ kho ra và truyền vào** qua constructor:

```java
public class RegisterUserUseCaseImpl {

    private final UserDomainService userDomainService;

    // Spring thấy constructor này → tự lấy bean từ kho → truyền vào
    public RegisterUserUseCaseImpl(UserDomainService userDomainService) {
        this.userDomainService = userDomainService;
    }
}
```

```
RegisterUserUseCaseImpl cần UserDomainService
            │
            ▼
Spring: "Tôi có bean này trong kho rồi!"
            │
            ▼
Spring tự động truyền vào constructor
            │
            ▼
RegisterUserUseCaseImpl hoạt động ✅
```

Quá trình này gọi là **Dependency Injection (DI)**.

---

## Tóm tắt

| Thứ | Vai trò | Ví dụ |
|---|---|---|
| **Bean** | Object do Spring quản lý trong kho | `UserDomainService`, `PasswordEncoder` |
| **`@Bean`** | Công thức tạo một bean cụ thể | Method trả về `new UserDomainService(...)` |
| **`@Configuration`** | Quyển sổ chứa các công thức `@Bean` | `UserDomainConfig` |
| **Inject** | Spring tự lấy bean từ kho truyền vào class cần | Constructor nhận `UserDomainService` |
