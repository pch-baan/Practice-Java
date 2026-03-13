# Aggregate trong DDD là gì?

## Aggregate là gì?

**Aggregate** = một nhóm objects (entity + value object) được xử lý như **một đơn vị thống nhất**,
có một **Aggregate Root** làm cửa vào duy nhất.

Ví dụ Order trong e-commerce:

```
┌─────────────────────────────────────────┐
│           Order (Aggregate Root)        │
│  id, status, totalAmount                │
│                                         │
│  ┌─────────────┐   ┌─────────────────┐  │
│  │  OrderItem  │   │  ShippingAddress │  │
│  │  (Entity)   │   │  (Value Object) │  │
│  └─────────────┘   └─────────────────┘  │
└─────────────────────────────────────────┘
         ↑
   Aggregate Root = "cửa vào duy nhất"
   Bên ngoài KHÔNG được sờ trực tiếp vào OrderItem
   Phải đi qua Order.addItem(), Order.removeItem()
```

---

## User trong project này có phải Aggregate không?

**Có — nhưng là trivial aggregate** (aggregate đơn giản nhất có thể):

```
┌─────────────────────────────────────────┐
│           User (Aggregate Root)         │
│                                         │
│  ┌────────────┐  ┌──────────────────┐   │
│  │  EmailVO   │  │  UsernameVO      │   │
│  │ (Value Obj)│  │  (Value Object)  │   │
│  └────────────┘  └──────────────────┘   │
│                                         │
│  ┌──────────────────┐                   │
│  │  PasswordHashVO  │                   │
│  │  (Value Object)  │                   │
│  └──────────────────┘                   │
└─────────────────────────────────────────┘
```

Chỉ có 1 entity (User) + vài Value Objects → **trivial aggregate**.

---

## Khi nào User "thật sự" là Aggregate?

Nếu User có thêm child entities:

```
┌──────────────────────────────────────────────────┐
│  User (Aggregate Root)                           │
│                                                  │
│  ┌──────────────────┐  ┌──────────────────────┐  │
│  │  UserProfile     │  │  UserAddress         │  │
│  │  (Entity)        │  │  (Entity, nhiều địa) │  │
│  └──────────────────┘  └──────────────────────┘  │
│                                                  │
│  ┌──────────────────┐                            │
│  │  OAuthProvider   │  ← Google/Facebook login   │
│  │  (Entity)        │                            │
│  └──────────────────┘                            │
└──────────────────────────────────────────────────┘

Khi đó: bên ngoài KHÔNG gọi userProfile.update() trực tiếp
        mà phải gọi user.updateProfile(...) → User root kiểm soát
```

---

## UserCredential có phải User aggregate không?

Không. `UserCredential` là **read model** — projection của `users` table dùng riêng cho auth context:

```
users table (owned by user-service)
        │
        │  SELECT id, username, password_hash, role, status
        ▼
UserCredential  ← snapshot tạm thời, không persist lại
```

Chính xác hơn: `UserCredential` không phải là User entity, và càng không phải User aggregate root.
Nó chỉ là Value Object / Read Model.

---

## Tóm tắt

| Thuật ngữ | Nghĩa | Ví dụ trong project |
|---|---|---|
| **Entity** | Object có identity (UUID), có lifecycle | `User`, `RefreshToken` |
| **Value Object** | Immutable, equality = so sánh data | `EmailVO`, `UserCredential` |
| **Aggregate** | Nhóm entity + VO, có 1 Aggregate Root | `User` + VOs của nó |
| **Aggregate Root** | Entity "cửa vào" của aggregate | `User` (root), `Order` (root) |
| **Trivial Aggregate** | Aggregate chỉ có 1 entity, không có child entity | `User` hiện tại |
