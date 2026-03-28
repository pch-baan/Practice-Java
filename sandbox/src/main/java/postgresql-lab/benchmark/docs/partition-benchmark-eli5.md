# Giải thích Partition Benchmark như bạn 5 tuổi học PostgreSQL

---

## Câu chuyện: Cái Kho Hàng Khổng Lồ

Tưởng tượng bạn có **5 triệu đơn hàng** trong một cái kho:

```
📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦
📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦📦
... (5,000,005 thùng hàng!)
```

Khi bạn cần tìm **"đơn hàng PENDING trong 30 ngày gần đây"**, bạn phải làm gì?

---

## 3 Cách Tổ Chức Kho

### Cách 1 — Baseline: Kho Lộn Xộn (Regular Table)

```
┌──────────────────────────────────────────────┐
│  KHO LỘN XỘN                                 │
│  📦PENDING 📦SHIPPED 📦PENDING 📦DELIVERED   │
│  📦PENDING 📦CONFIRMED 📦PENDING 📦SHIPPED   │
│  ... 5 triệu thùng trộn lẫn nhau ...         │
└──────────────────────────────────────────────┘
```

Muốn tìm đơn PENDING? → **Phải lật từng thùng một** → mất **263ms**

---

### Cách 2 — LIST Partition: Chia Kho Theo Trạng Thái

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ KHO PENDING  │  │ KHO CONFIRMED│  │ KHO SHIPPED  │
│  📦📦📦📦   │  │  📦📦📦📦   │  │  📦📦📦📦   │
└──────────────┘  └──────────────┘  └──────────────┘
```

Muốn tìm PENDING? → Vào **đúng kho** → **0.4ms** ⚡ Tuyệt!

**NHƯNG có vấn đề lớn...**

Khi đơn hàng thay đổi trạng thái `PENDING → CONFIRMED`:

```
❌ PHẢI LÀM:
1. Xé nhãn thùng khỏi KHO PENDING    (DELETE)
2. Mang thùng sang KHO CONFIRMED      (INSERT)

× 1000 đơn = 1000 lần xé + 1000 lần chuyển
= 2,000 thao tác thay vì 1,000 ← chậm 130x!
```

Kết quả: Bulk update 1000 đơn → từ **7.8ms** tăng lên **1,018ms** 💀

---

### Cách 3 — RANGE Partition: Chia Kho Theo Tháng ✅ WINNER

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  Tháng 1    │  │  Tháng 2    │  │  Tháng 3    │
│  ~200K đơn  │  │  ~200K đơn  │  │  ~200K đơn  │  ...
└─────────────┘  └─────────────┘  └─────────────┘
    (cũ)             (cũ)           (mới nhất)
```

Tìm đơn PENDING 30 ngày gần đây? → PostgreSQL biết ngay:

```
"30 ngày gần nhất = Tháng 2 + Tháng 3 thôi"
→ Chỉ mở 2 trong 31 kho
→ 0.6ms thay vì 263ms ← nhanh 400x! 🚀
```

Khi đơn `PENDING → CONFIRMED`? → **Không cần chuyển kho** vì kho phân theo ngày tạo — đơn nằm **tại chỗ**, chỉ đổi giá trị trạng thái → Update vẫn nhanh!

---

## Kết Quả Đo Thực Tế

```
                    Baseline   LIST(status)  RANGE(created_at)
                    ─────────  ────────────  ─────────────────
Tìm PENDING 30 ngày  263ms        —              0.6ms  ← 400x 🚀
Tìm PENDING tất cả   263ms        0.4ms          1.1ms
Update 1 đơn         0.38ms       0.9ms          1.2ms  ← OK
Bulk update 1000 đơn  7.8ms    1,018ms ❌        57.8ms ← 7x (chấp nhận)
Tổng hợp theo tháng 3,066ms      598ms           679ms  ← 4.5x 🚀
```

---

## Tại Sao LIST Bị Loại?

```
Vòng đời 1 đơn hàng:
PENDING → CONFIRMED → SHIPPED → DELIVERED

Mỗi mũi tên = 1 lần "dọn kho"
Hàng nghìn đơn/giờ = Kho liên tục hỗn loạn → 💥 bottleneck
```

LIST partition chỉ tốt khi data **không bao giờ đổi phân vùng** sau khi tạo — ví dụ: chia theo `country_code`, `tenant_id`.

---

## Cái Hay Nhất của RANGE Partition

```
Đơn hàng cũ hơn 1 năm?

  DETACH PARTITION orders_y2024m01;
  ↓
  Tách ra, archive vào S3 hoặc xóa
  ↓
  Table chính nhẹ hơn, query tiếp tục nhanh
```

Như dọn kho thật: **thùng cũ đóng gói riêng**, không ảnh hưởng hoạt động hàng ngày.

---

## Trade-off Cần Nhớ

| Đánh đổi | Nghiêm trọng? | Cách xử lý |
|---|---|---|
| Bulk UPDATE chậm hơn 7x | Thấp | Thêm `AND created_at >= NOW() - '7 days'` |
| FK `order_items → orders` bị xóa | Trung bình | JPA validate ở tầng application |
| Phải tạo partition mỗi tháng | Thấp | Có script `04_add_monthly_partition.sql` sẵn |
| PK đổi thành `(id, created_at)` | Trung bình | Query by `id` vẫn hoạt động bình thường |

---

## Câu Thần Chú

> **"LIST partition = chia theo loại → tốt khi data đứng yên"**
> **"RANGE partition = chia theo thời gian → tốt khi data có lifecycle"**

`orders` có lifecycle thay đổi status liên tục → **RANGE wins** 🏆
