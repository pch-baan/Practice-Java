# UserDomainConfig — Active Recall

---

## Q1: Tại sao cần `UserDomainConfig`?

<details>
<summary>Trả lời</summary>

`UserDomainService` là pure Java (không có `@Service`) → Spring không biết nó tồn tại → không tạo bean.

`UserDomainConfig` dùng `@Bean` để **đăng ký thủ công** `UserDomainService` vào Spring container.

</details>

---

## Q2: Nếu không có `UserDomainConfig`, lỗi gì xảy ra?

<details>
<summary>Trả lời</summary>

```
NoSuchBeanDefinitionException: No qualifying bean of type 'UserDomainService'
```

Vì `CreateUserUseCaseImpl` inject `UserDomainService` nhưng Spring không tìm thấy bean nào.

</details>

---

## Q3: Tại sao không để `@Service` thẳng trên `UserDomainService` cho đơn giản?

<details>
<summary>Trả lời</summary>

Vì `architecture.md` quy định: **`domain/` chỉ được import `java.*`**.

`@Service` là `org.springframework.stereotype.Service` → vi phạm rule.

Domain phải là pure Java để có thể tách ra khỏi Spring bất cứ lúc nào.

</details>

---

## Q4: Tại sao bean registration nằm ở `infrastructure/config/` mà không phải layer khác?

<details>
<summary>Trả lời</summary>

| Layer | Được import Spring? | Có nên đặt wiring? |
|---|---|---|
| `domain/` | Không | Không |
| `application/` | Được (dùng @Service, @Transactional) | Không — nơi điều phối logic, không phải lắp ráp |
| `infrastructure/` | Được | **Có** — đây là nơi "lắp ráp" mọi thứ |

`infrastructure/config/` là nơi quy ước để wire toàn bộ dependency, giống `SecurityConfig` wire `BCryptPasswordEncoder`.

</details>

---

## Q5: `UserDomainService` được dùng ở đâu?

<details>
<summary>Trả lời</summary>

Chỉ 1 nơi: `CreateUserUseCaseImpl` (tầng `application/usecase/`).

```java
userDomainService.validateUniqueConstraints(email, username);
```

Kiểm tra email và username chưa tồn tại trong DB trước khi tạo user mới.

</details>
