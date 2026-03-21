# CreateUserUseCaseImpl — Phụ thuộc vào Domain

## Vị trí trong kiến trúc

```
api-portal (Controller)
    ↓
application/usecase/CreateUserUseCaseImpl   ← file này
    ↓
domain/ (Entity, Value Object, Port, Service)
    ↓
infrastructure/ (adapter thực thi domain port)
```

`CreateUserUseCaseImpl` nằm ở layer **application**. Nhiệm vụ của nó là **điều phối luồng nghiệp vụ** — không tự xử lý logic, mà gọi vào domain để thực thi.

---

## Bằng chứng phụ thuộc domain

### Import trực tiếp từ domain

```java
import com.practice.user.domain.model.User;                    // Domain Entity
import com.practice.user.domain.port.out.IUserRepository;      // Domain Port (interface)
import com.practice.user.domain.service.UserDomainService;     // Domain Service
import com.practice.user.domain.valueobject.EmailVO;           // Domain Value Object
import com.practice.user.domain.valueobject.UsernameVO;        // Domain Value Object
```

5 import đều trỏ vào package `com.practice.user.domain.*`.

---

### Trong method `execute()`

| Dòng | Lời gọi | Loại domain object |
|------|---------|-------------------|
| 27 | `EmailVO.of(command.email())` | Value Object — tự validate định dạng email |
| 28 | `UsernameVO.of(command.username())` | Value Object — tự validate định dạng username |
| 30 | `userDomainService.validateUniqueConstraints(email, username)` | Domain Service — kiểm tra ràng buộc nghiệp vụ |
| 34 | `User.create(...)` | Domain Entity — factory method tạo aggregate |
| 36 | `userRepository.save(user)` | Domain Port — interface định nghĩa trong domain |

---

## Nguyên tắc Dependency Rule

```
application  →  domain    ✅ (đúng chiều)
domain       →  application  ✗ (vi phạm — không được xảy ra)
```

`CreateUserUseCaseImpl` KHÔNG import bất kỳ thứ gì từ `infrastructure/`.
Infrastructure (JPA adapter, PostgreSQL) implement `IUserRepository` — domain port — và được inject vào qua Spring DI.
UseCase chỉ biết đến interface, không biết implementation cụ thể.

---

## Tóm tắt vai trò từng thành phần

| Thành phần | Layer | Vai trò trong use case này |
|-----------|-------|--------------------------|
| `EmailVO`, `UsernameVO` | Domain | Bao bọc và validate dữ liệu đầu vào |
| `UserDomainService` | Domain | Kiểm tra email/username chưa tồn tại |
| `User.create(...)` | Domain | Tạo aggregate với trạng thái hợp lệ |
| `IUserRepository` | Domain (port) | Hợp đồng lưu trữ, không phụ thuộc DB cụ thể |
| `CreateUserUseCaseImpl` | Application | Kết nối các mảnh trên thành một luồng hoàn chỉnh |
