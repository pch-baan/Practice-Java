# Lombok Annotations — Pareto 20/80

> 7 annotations này xuất hiện trong ~80% codebase Java thực tế.

---

## 1. `@Data` — gói combo cho DTO / POJO

```java
@Data
public class UserDto {
    private String name;
    private String email;
}
```

Tự sinh ra: `@Getter` + `@Setter` + `@ToString` + `@EqualsAndHashCode` + `@RequiredArgsConstructor`

**Dùng ở đâu:** DTO, request/response object, POJO đơn giản.
**Không dùng ở đâu:** JPA Entity (dễ gây lỗi `equals/hashCode` với lazy loading), Domain Entity.

---

## 2. `@Builder` — tạo object có nhiều field mà không cần constructor dài

```java
// Không có @Builder — gọi constructor dễ nhầm thứ tự 😫
new User("Alice", "alice@example.com", 25, "ACTIVE", "USER");

// Có @Builder — rõ ràng, an toàn ✨
@Builder
public class User { ... }

User user = User.builder()
    .name("Alice")
    .email("alice@example.com")
    .age(25)
    .build();
```

**Dùng ở đâu:** DTO, command object, test fixture (tạo data mẫu trong test).

---

## 3. `@RequiredArgsConstructor` — inject dependency trong Spring

```java
// Thay thế @Autowired field injection (bad practice)
@Service
@RequiredArgsConstructor        // sinh constructor cho tất cả final fields
public class UserService {
    private final UserRepository userRepository;   // ← tự inject
    private final PasswordEncoder passwordEncoder; // ← tự inject
}
```

**Dùng ở đâu:** Mọi `@Service`, `@Component`, `@RestController` dùng constructor injection.
**Ghi chú:** Spring tự detect constructor duy nhất → không cần `@Autowired`.

---

## 4. `@Slf4j` — logging không cần khai báo thủ công

```java
// Không có @Slf4j — phải khai báo mỗi class 😫
private static final Logger log = LoggerFactory.getLogger(UserService.class);

// Có @Slf4j — dùng luôn biến `log` ✨
@Slf4j
@Service
public class UserService {
    public void createUser() {
        log.info("Creating user...");
        log.warn("Something suspicious");
        log.error("Something went wrong", exception);
    }
}
```

**Dùng ở đâu:** Mọi class cần logging — service, controller, scheduler...

---

## 5. `@NoArgsConstructor` + `@AllArgsConstructor` — bắt buộc cho JPA Entity

```java
@Entity
@Getter
@NoArgsConstructor              // JPA bắt buộc có constructor không tham số
@AllArgsConstructor             // tiện tạo object đầy đủ
public class UserEntity {
    @Id
    private UUID id;
    private String name;
    private String email;
}
```

**Dùng ở đâu:** JPA Entity — thiếu `@NoArgsConstructor` là JPA báo lỗi ngay.

---

## 6. `@Value` — immutable object (không có setter)

```java
@Value                          // tất cả fields là final, không có setter
public class Money {
    BigDecimal amount;
    String currency;
}

// Sử dụng:
Money price = new Money(new BigDecimal("100.00"), "USD");
// price.setAmount(...) → compile error ✅
```

**Dùng ở đâu:** Value Object trong DDD, config object, response object không cần thay đổi.
**Khác `@Data`:** `@Value` = immutable, `@Data` = mutable.

---

## 7. `@NonNull` — tự động check null, không cần viết if thủ công

```java
// Không có @NonNull — phải tự viết 😫
public void createUser(String name) {
    if (name == null) throw new NullPointerException("name is null");
    ...
}

// Có @NonNull — Lombok tự sinh đoạn check đó ✨
public void createUser(@NonNull String name) {
    // Lombok tự sinh: if (name == null) throw NullPointerException
    ...
}

// Cũng dùng được trên field:
@NonNull
private String email; // setter sẽ tự check null trước khi gán
```

**Dùng ở đâu:** Tham số method, field — bất cứ chỗ nào null là bug chắc chắn.

---

## Tóm tắt — dùng annotation nào ở layer nào?

| Layer | Annotation hay dùng |
|---|---|
| **REST Controller** | `@RequiredArgsConstructor`, `@Slf4j` |
| **Service** | `@RequiredArgsConstructor`, `@Slf4j` |
| **DTO / Request / Response** | `@Data`, `@Builder`, `@Value` |
| **JPA Entity** | `@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor` |
| **Domain Entity** | Không dùng Lombok |
| **Value Object** | `@Value` hoặc không dùng Lombok |
