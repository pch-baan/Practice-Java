# Lombok Patterns — Combo hay dùng trong thực tế

---

## 1. `@Builder.Default` — giá trị mặc định với Builder

**Vấn đề cốt lõi:** `@Builder` bỏ qua giá trị default khai báo inline.

```java
// ❌ Sai — default bị ignore
@Builder
public class Order {
    private String status = "PENDING"; // bị bỏ qua khi dùng builder
}

Order o = Order.builder().build();
System.out.println(o.getStatus()); // → null 😱

// ✅ Đúng
@Builder
public class Order {
    @Builder.Default
    private String status = "PENDING";
}

Order o = Order.builder().build();
System.out.println(o.getStatus()); // → "PENDING" ✅
```

---

## 2. `@Data` + `@Builder` — combo cho DTO

Dùng khi cần cả **tạo linh hoạt** (Builder) lẫn **getter/setter/equals** (@Data).

```java
@Data
@Builder
@NoArgsConstructor   // bắt buộc kèm theo
@AllArgsConstructor  // bắt buộc kèm theo
public class CreateUserRequest {
    private String name;
    private String email;
    private int age;
}

// Tạo object bằng Builder:
CreateUserRequest req = CreateUserRequest.builder()
    .name("Alice")
    .email("alice@example.com")
    .age(25)
    .build();

// Dùng getter bình thường:
req.getName(); // ✅
```

---

## 3. JPA Entity — combo chuẩn

```java
@Getter
@Setter
@NoArgsConstructor                              // JPA bắt buộc
@AllArgsConstructor                             // tiện tạo object
@ToString(exclude = {"orders", "roles"})        // exclude lazy fields
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
public class User {

    @Id @GeneratedValue
    @EqualsAndHashCode.Include                  // chỉ so sánh theo id
    private UUID id;

    private String name;
    private String email;

    @OneToMany(fetch = LAZY, mappedBy = "user")
    private List<Order> orders;                 // lazy — đã exclude khỏi toString
}
```

---

## Tóm tắt — chọn combo theo use case

| Use case | Combo |
|---|---|
| DTO / Request / Response | `@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` |
| Immutable Response | `@Value` + `@Builder` |
| JPA Entity | `@Getter` + `@Setter` + `@NoArgsConstructor` + `@ToString(exclude)` + `@EqualsAndHashCode(onlyExplicitlyIncluded)` |
| Spring Service | `@RequiredArgsConstructor` + `@Slf4j` |
