# Lombok là gì? 🪄

---

## Câu chuyện: Cái máy photocopy thần kỳ

Tưởng tượng bạn là học sinh, và mỗi ngày thầy giáo bắt bạn chép tay bài này vào vở:

```
Họ tên: Nguyễn Văn A
Lớp: 5A
Trường: Tiểu học ABC
Ngày sinh: 01/01/2015
Địa chỉ: Hà Nội
```

Mỗi môn học đều phải chép lại. **10 môn = chép 10 lần.** 😩

---

Một ngày, bạn có **cái máy photocopy thần kỳ** — chỉ cần dán tờ giấy nhỏ ghi `@ThongTinHocSinh` lên vở, máy **tự động in ra toàn bộ** cho bạn. ✨

```
📋 Vở môn Toán:

  @ThongTinHocSinh   ← chỉ cần dán nhãn này

  ✅ Máy tự in ra:
     Họ tên, Lớp, Trường, Ngày sinh, Địa chỉ...
```

---

## Trong Java cũng vậy

Mỗi khi tạo một class, bạn phải viết đi viết lại những đoạn **code nhàm chán, lặp đi lặp lại**:

```java
// Không có Lombok — phải viết tay 😫
public class User {
    private String name;
    private String email;

    public String getName() { return name; }              // getter
    public void setName(String name) { this.name = name; } // setter
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean equals(Object o) { ... }   // 10 dòng
    public int hashCode() { ... }             // 5 dòng
    public String toString() { ... }          // 5 dòng
}
// Tổng: ~30 dòng chỉ để làm mấy việc nhàm chán
```

**Lombok** = cái máy photocopy thần kỳ. Chỉ cần dán nhãn `@`:

```java
// Có Lombok — xong ngay! 🎉
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class User {
    private String name;
    private String email;
}
// Tổng: 6 dòng — Lombok tự sinh ra 30 dòng kia lúc compile
```

---

## Lombok làm việc lúc nào?

```
Bạn code          Lombok chạy          Java chạy
──────────        ────────────         ──────────
@Getter      →   sinh getName()    →   .class file
@Setter      →   sinh setName()    →   chạy bình thường
@ToString    →   sinh toString()   →
```

Lombok **không chạy lúc runtime** — nó chạy **lúc compile**, tự viết code thay bạn, rồi biến mất.

---

## Các nhãn dán phổ biến

→ Xem chi tiết: [annotations.md](annotations.md)

---

> **Tóm lại:** Lombok = trợ lý robot viết giúp bạn những đoạn code nhàm chán, lặp đi lặp lại trong Java. Bạn chỉ cần dán nhãn `@`, nó tự làm phần còn lại. 🤖
