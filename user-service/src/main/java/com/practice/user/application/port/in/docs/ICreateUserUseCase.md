# ICreateUserUseCase

## Mục đích

`ICreateUserUseCase` là **Inbound Port** trong kiến trúc Hexagonal Architecture.

Nó định nghĩa "hợp đồng" (contract) cho use case tạo user — là ranh giới giữa tầng `api-portal` (controller) và tầng `application` (business logic).

## Vị trí trong kiến trúc

```
[UserController]          [ICreateUserUseCase]          [CreateUserUseCaseImpl]
  (api-portal)    ──────>     (port/in)        <──────      (application)
                           (interface/contract)           (implementation)
```

Theo Dependency Rule:

```
api-portal (controllers) → application (UseCases) → domain
```

Controller chỉ biết đến interface `ICreateUserUseCase`, không biết đến implementation cụ thể.

## Lý do tách interface

| Lý do | Giải thích |
|---|---|
| **Dependency Inversion** | Controller phụ thuộc vào abstraction, không phụ thuộc vào impl cụ thể |
| **Testability** | Có thể mock interface trong unit test của controller |
| **Decoupling** | Đổi implementation mà không cần sửa controller |

## Luồng dữ liệu

```
HTTP Request
    ↓
UserController.createUser(request)
    ↓
ICreateUserUseCase.execute(CreateUserCommandDto)
    ↓
CreateUserUseCaseImpl (xử lý business logic)
    ↓
UserResponseDto (trả về controller)
    ↓
HTTP Response
```
