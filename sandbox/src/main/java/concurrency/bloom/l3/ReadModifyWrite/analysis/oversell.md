# 🎂 Oversell — Bán Nhiều Hơn Số Hàng Có Trong Kho

## Định nghĩa

**Oversell** = bán vượt quá số lượng thực tế đang có → dữ liệu không nhất quán → thiệt hại thực tế cho business.

---

## Câu chuyện thực tế

Cửa hàng có **1 cái bánh** cuối cùng. 2 khách cùng lúc gọi điện đặt mua:

```
Kho: [🎂]

Nhân viên A nghe điện thoại khách 1:
  "Còn bánh không?" → nhìn kho → CÒN! → "OK anh đặt được!"

                                    ↕ cùng lúc

Nhân viên B nghe điện thoại khách 2:
  "Còn bánh không?" → nhìn kho → CÒN! → "OK chị đặt được!"

Kết quả:
  Khách 1: đã đặt ✅
  Khách 2: đã đặt ✅
  Kho: âm 1 cái 😱

Cửa hàng phải gọi lại xin lỗi 1 trong 2 người → mất uy tín
```

---

## Trong code

```
stock = 1

Thread A: đọc stock=1 → OK → [Thread B chen vào] → stock = stock - 1 = 0 → SOLD ✅
Thread B: đọc stock=1 → OK →                     → stock = stock - 1 = -1 → SOLD ✅

Kết quả: 2 người mua được, kho = -1 💥
```

Pattern gây ra: **Read-Modify-Write** không atomic

```java
if (stock >= 1) {       // READ   ← thread khác chen vào đây
    stock = stock - 1;  // MODIFY + WRITE
    // sold!
}
```

---

## Ngoài thực tế hay gặp ở đâu?

| Tình huống | Oversell là... |
|---|---|
| Vé concert | 1000 vé nhưng bán được 1200 |
| Flash sale Shopee | Còn 1 sản phẩm nhưng 3 người checkout thành công |
| Đặt phòng khách sạn | Phòng đã có người đặt nhưng vẫn cho đặt tiếp |
| Vé máy bay | Overbooking — hãng bay cố tình bán dư để phòng trường hợp hủy |

---

## Fix: `@Version` — Optimistic Locking

```java
@Version
private Long version;
// JPA tự generate:
// UPDATE products SET stock=0, version=1 WHERE id=1 AND version=0
// Nếu version đã đổi → 0 rows updated → exception → không oversell
```

> Xem chi tiết trong `analysis.md`.
