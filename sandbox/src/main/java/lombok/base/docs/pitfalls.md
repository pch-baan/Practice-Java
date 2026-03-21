# Lombok Pitfalls — Bẫy hay mắc

> Từ khoá chưa quen? → [glossary.md](0-glossary.md)

---

## 1. `@Data` trên JPA Entity → LazyInitializationException

**Vấn đề cốt lõi:** `@ToString` (nằm trong `@Data`) tự động in ra tất cả fields,
kể cả relation `@OneToMany(fetch = LAZY)` → Hibernate phải load data từ DB →
nếu session đã đóng thì **nổ exception**.

> `LAZY` — chỉ load từ DB khi bị gọi, không load
> trước. → [glossary.md#lazy--lazy-loading](0-glossary.md#lazy--lazy-loading)
> `exclude` — loại field đó ra, không đụng vào. → [glossary.md#exclude](0-glossary.md#exclude)

```java
// ❌ Sai
@Data
@Entity
public class Order {
    @OneToMany(fetch = LAZY)
    private List<Item> items; // @ToString đụng vào đây → boom 💥
}

// ✅ Đúng — exclude field lazy ra khỏi toString
@Getter
@Setter
@ToString(exclude = "items")  // bỏ qua items → không trigger query → không nổ
@Entity
public class Order {
    @OneToMany(fetch = LAZY)
    private List<Item> items;
}
```

**Quy tắc:** JPA Entity **không dùng `@Data`** — dùng `@Getter` + `@Setter` +
`@ToString(exclude = ...)`.

---

## 2. `@Data` + `@Builder` → compile error

**Vấn đề cốt lõi:** `@Builder` sinh constructor *tất cả fields* (package-private).
`@Data` sinh `@RequiredArgsConstructor`. Hai cái đụng nhau → compile error.

```java
// ❌ Sai
@Data
@Builder
public class UserDto {
    private String name;
    private String email;
}
// Error: constructor already defined

// ✅ Đúng — khai báo rõ cả hai constructor
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private String name;
    private String email;
}
```

**Quy tắc:** Dùng `@Data` + `@Builder` thì phải thêm `@NoArgsConstructor` + `@AllArgsConstructor`.

---

## 3. `@EqualsAndHashCode` trên JPA Entity → bug với HashSet / HashMap

**Vấn đề cốt lõi:** `@EqualsAndHashCode` dùng tất cả fields để tính hash.
JPA Entity thay đổi state theo thời gian (sau khi persist, `id` thay đổi từ `null` → value)
→ hashCode thay đổi → **object bị mất trong HashSet**.

```java
// ❌ Sai
@EqualsAndHashCode  // hash tính theo id + name + email → id null lúc đầu
@Entity
public class User {
    @Id @GeneratedValue
    private UUID id; // null trước khi save, có value sau khi save
}

// ✅ Đúng — chỉ dùng id
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
public class User {
    @Id @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;
}
```

**Quy tắc:** JPA Entity chỉ `equals/hashCode` theo `id` duy nhất.
