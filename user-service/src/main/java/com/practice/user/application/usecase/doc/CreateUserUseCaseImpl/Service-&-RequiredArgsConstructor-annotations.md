# @Service và @RequiredArgsConstructor — Tại sao đặt ở Impl?

## Nguyên tắc cốt lõi

```
ICreateUserUseCase (interface)   ← hợp đồng: định nghĩa "phải làm gì"
CreateUserUseCaseImpl (class)    ← thực thi: định nghĩa "làm thế nào"
```

Annotation luôn đặt ở **Impl** vì interface không có body, không có field, không thể được khởi tạo.

---

## @Service

Nói với Spring IoC Container: _"Hãy tạo 1 instance của class này và quản lý nó."_

```java
// ✅ Đúng — đặt ở Impl
@Service
public class CreateUserUseCaseImpl implements ICreateUserUseCase { ... }

// ❌ Sai — đặt ở interface, Spring không thể new interface
@Service
public interface ICreateUserUseCase { ... }
```

Khi một class khác cần `ICreateUserUseCase` (ví dụ controller), Spring nhìn vào container,
tìm bean nào implement interface đó, và inject `CreateUserUseCaseImpl` vào.

```
Controller yêu cầu ICreateUserUseCase
    ↓
Spring tìm trong container: "ai implement interface này?"
    ↓
Tìm thấy CreateUserUseCaseImpl (@Service)
    ↓
Inject vào Controller
```

---

## @RequiredArgsConstructor

Annotation của **Lombok**, sinh constructor tự động cho tất cả field `final`.

```java
// Bạn viết:
@RequiredArgsConstructor
public class CreateUserUseCaseImpl {
    private final UserDomainService userDomainService;
    private final IUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
}

// Lombok sinh ra ngầm:
public CreateUserUseCaseImpl(
    UserDomainService userDomainService,
    IUserRepository userRepository,
    PasswordEncoder passwordEncoder
) {
    this.userDomainService = userDomainService;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
}
```

Constructor này là thứ Spring dùng để **inject dependency** (Constructor Injection).
Interface không có field → Lombok không có gì để sinh → annotation vô nghĩa nếu đặt ở interface.

---

## Tóm tắt

| Annotation | Đặt ở | Lý do |
|-----------|-------|-------|
| `@Service` | Impl | Spring cần class cụ thể để khởi tạo, không thể new interface |
| `@RequiredArgsConstructor` | Impl | Field `final` chỉ có ở Impl, Lombok mới có thứ để sinh constructor |

**Interface chỉ là hợp đồng** — không quan tâm "làm thế nào" hay "lấy dependency từ đâu".
