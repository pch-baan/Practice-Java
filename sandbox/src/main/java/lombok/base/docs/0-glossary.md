# Glossary — Từ khoá hay gặp

---

## exclude

**Nghĩa đơn giản:** loại ra, bỏ qua, không tính đến.

```
Chụp ảnh lớp — "exclude bạn An đang vắng"
→ Ảnh có tất cả mọi người, KHÔNG có An.
```

Trong Lombok:

```java
@ToString(exclude = "items")
// In object ra → bỏ qua field `items`, in tất cả fields còn lại
```

---

## lazy / lazy loading

**Nghĩa đơn giản:** lười biếng — chỉ làm khi bị hỏi, không làm trước.

```
Eager (chăm):                    Lazy (lười):
─────────────────────────        ─────────────────────────
Vào lớp mở sách đọc hết          Ngồi chơi, chưa mở sách.
500 trang ngay lập tức. 😓       Thầy hỏi trang nào mới
                                 lật ra đọc trang đó. 😴
```

Trong JPA — `fetch = LAZY`, cụ thể hơn bằng SQL thực tế:

```
Bước 1 — gọi orderRepo.findById(1):
  SQL chạy: SELECT id, status, date FROM orders WHERE id = 1
  items:    (không có gì) — Hibernate chưa chạy query nào cho items

Bước 2 — gọi order.getItems():  ← lúc NÀY mới trigger
  SQL chạy: SELECT * FROM items WHERE order_id = 1
  items:    [Item A, Item B, Item C]  ← bây giờ mới có
```

So sánh với `fetch = EAGER`:

```
Bước 1 — gọi orderRepo.findById(1):
  SQL chạy: SELECT id, status, date FROM orders WHERE id = 1
            SELECT * FROM items WHERE order_id = 1   ← chạy luôn, dù cần hay không
  items:    [Item A, Item B, Item C]  ← có ngay, nhưng tốn query không cần thiết
```

**Tại sao cần biết?** →
Xem [pitfalls.md](pitfalls.md#1-data-trên-jpa-entity--lazyinitializationexception)

---

## JPA

**Nghĩa đơn giản:** bộ quy tắc (tiêu chuẩn) quy định cách Java lưu object xuống database.

```
Không có JPA:                     Có JPA:
──────────────────────────        ──────────────────────────
Tự viết SQL:                      Chỉ cần khai báo class:
  INSERT INTO users                 @Entity
  VALUES (?, ?, ?)                  public class User { ... }
  SELECT * FROM users             JPA tự lo phần SQL.
  WHERE id = ?
  ... (lặp mãi)
```

JPA là **tiêu chuẩn** (như bộ luật) — còn ai thực thi bộ luật đó là Hibernate.

> JPA = bản thiết kế.  Hibernate = người thợ xây theo bản thiết kế đó.

---

## Hibernate

**Nghĩa đơn giản:** thư viện thực thi JPA — tự động chuyển đổi qua lại giữa Java object và database.

```
Java world              Hibernate làm cầu nối          Database world
────────────            ───────────────────────         ──────────────
User user               save(user)  →  INSERT SQL  →   bảng users
  name = "Alice"    ←   find(1)     ←  SELECT SQL  ←   id=1, name='Alice'
  email = "..."
```

Hibernate lo:
- Sinh SQL tự động (không cần viết tay)
- Quản lý connection đến DB
- Cache, lazy loading, transaction

**Tại sao hay nhắc đến Hibernate trong Lombok docs?**
Vì Hibernate có cơ chế lazy loading — và Lombok `@ToString` hay vô tình trigger nó. → Xem [lazy](#lazy--lazy-loading)

---

## boilerplate

**Nghĩa đơn giản:** đoạn code nhàm chán, lặp đi lặp lại, không chứa logic gì đặc biệt.

```
getter, setter, equals, hashCode, toString
→ viết ở mọi class, na ná nhau, không thú vị gì.
→ Lombok sinh ra để xoá bỏ boilerplate này.
```

---

## compile time vs runtime

|                       | compile time                 | runtime                         |
|-----------------------|------------------------------|---------------------------------|
| **Khi nào**           | Lúc build (mvn package)      | Lúc chạy app                    |
| **Lombok làm việc ở** | ✅ compile time               | ❌ không có mặt                  |
| **Ví dụ lỗi**         | syntax error, missing method | NullPointerException, exception |

```
Bạn viết @Getter   →   Lombok sinh getName()   →   .class file   →   App chạy
         ↑                      ↑                        ↑
    compile time           compile time              runtime
    (Lombok còn đây)       (Lombok làm việc)         (Lombok biến mất)
```
