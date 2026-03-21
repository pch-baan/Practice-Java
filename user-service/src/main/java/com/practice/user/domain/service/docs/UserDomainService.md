# UserDomainService — Domain Service

## Mục đích

`UserDomainService` là một **Domain Service** trong kiến trúc DDD — chứa logic nghiệp vụ **không thuộc về một entity cụ thể nào**, và cần tham chiếu đến repository để thực hiện.

Cụ thể: kiểm tra ràng buộc unique của `email` và `username` trước khi tạo user mới.

## Vị trí trong kiến trúc

```
UseCase (Application)
        │
        │  gọi
        ▼
UserDomainService  ← Domain Service (domain logic cần query)
        │
        │  gọi qua port
        ▼
IUserRepository  ← Output Port
        ▲
        │  implement
        │
UserPostgresqlAdapter  ← Infrastructure
```

## Tại sao không đặt logic này trong `User` entity?

Entity `User` chỉ biết về chính nó. Để kiểm tra "email đã tồn tại chưa?" cần query database — đó là việc của repository.

**Entity không được phép gọi repository** — vi phạm nguyên tắc DDD.

`UserDomainService` là cầu nối: vẫn nằm trong tầng `domain/`, không phụ thuộc Spring hay JPA, nhưng được phép nhận repository qua constructor injection.

## So sánh

| | `User` entity | `UserDomainService` |
|---|---|---|
| Biết về chính mình | Có | Không |
| Gọi repository | Không (vi phạm DDD) | Có |
| Logic liên quan nhiều resource | Không | Có |
| Phụ thuộc framework | Không | Không |

## Các method

| Method | Mục đích |
|---|---|
| `validateUniqueConstraints(EmailVO, UsernameVO)` | Ném `UserDomainException` nếu email hoặc username đã tồn tại |
