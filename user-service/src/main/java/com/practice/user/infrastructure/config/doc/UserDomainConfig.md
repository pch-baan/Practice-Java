# UserDomainConfig — Spring Configuration

## Mục đích

`UserDomainConfig` là một **Spring `@Configuration`** — đóng vai trò cầu nối giữa tầng `domain/` và Spring IoC container.

`UserDomainService` nằm trong `domain/` nên không có `@Service`, không biết Spring tồn tại. `UserDomainConfig` tạo bean thủ công bằng `@Bean` và truyền dependency vào constructor — giữ cho domain sạch khỏi framework.

## Vị trí trong kiến trúc

```
Spring IoC
    │
    │  @Bean (tạo thủ công)
    ▼
UserDomainConfig  ← infrastructure/config (biết Spring)
    │
    │  new UserDomainService(userRepository)
    ▼
UserDomainService  ← domain/ (KHÔNG biết Spring)
```

## Các bean được đăng ký

| Bean | Kiểu | Mục đích |
|---|---|---|
| `userDomainService` | `UserDomainService` | Inject `IUserRepository` vào domain service |
| `passwordEncoder` | `BCryptPasswordEncoder` | Hash password khi đăng ký user |

## Tại sao không dùng `@Service` trực tiếp trên `UserDomainService`?

Tầng `domain/` là **Pure Java** — không được import bất kỳ annotation Spring nào. Đây là nguyên tắc cốt lõi của DDD: domain layer độc lập với framework.

`UserDomainConfig` là "cánh tay nối dài" của infrastructure — đưa domain vào Spring mà không làm domain bị nhiễm Spring.
