# Batch và Round-trip trong Database

## Round-trip là gì?

Mỗi lần app gửi câu lệnh SQL đến DB và chờ kết quả trả về = **1 round-trip**.

```
App                Network           Database
 |                                      |
 |-------- "INSERT user 1" ------------>|
 |<-------- "OK done" -----------------|   ← 1 round-trip
 |
 |-------- "INSERT user 2" ------------>|
 |<-------- "OK done" -----------------|   ← 1 round-trip
 |
 |-------- "INSERT user 3" ------------>|
 |<-------- "OK done" -----------------|   ← 1 round-trip
```

Mỗi round-trip tốn thời gian do độ trễ mạng (network latency ~1–5ms).
Với 1000 request → **1000 round-trips = rất chậm**.

---

## Batch là gì?

**Batch = gom nhiều câu lệnh SQL lại, gửi 1 lần duy nhất đến DB.**

```
App                Network           Database
 |                                      |
 |--- "INSERT user 1,2,3...1000" ------>|
 |<-------- "OK done all" -------------|   ← 1 round-trip duy nhất
```

Thay vì đi-về 1000 lần, chỉ đi-về **1 lần** → nhanh hơn rất nhiều.

---

## Tại sao `save()` batch được, `saveAndFlush()` thì không?

### `save()` — flush cuối transaction (batch)

```
save(user1)  ──┐
save(user2)  ──┤──► Hibernate cache (memory) ──► flush 1 lần ──► DB
save(user3)  ──┘                                              (batch, 1 round-trip)
```

Hibernate giữ các entity trong **1st-level cache (memory)**, chỉ ghi xuống DB
1 lần khi transaction kết thúc → gom thành batch → **1 round-trip**.

### `saveAndFlush()` — flush ngay lập tức (không batch được)

```
saveAndFlush(user1) ──► flush ngay ──► DB   ← round-trip 1
saveAndFlush(user2) ──► flush ngay ──► DB   ← round-trip 2
saveAndFlush(user3) ──► flush ngay ──► DB   ← round-trip 3
```

Mỗi lần gọi đều ghi ngay xuống DB → **không gom được** → N round-trips.

---

## Bảng so sánh

|                  | `save()`              | `saveAndFlush()`         |
|------------------|-----------------------|--------------------------|
| Khi nào ghi DB   | Cuối transaction      | Ngay lập tức             |
| Round-trips      | 1 (batch)             | N (mỗi lần 1 cái)        |
| Tốc độ           | Nhanh                 | Chậm hơn                 |
| Dùng khi nào     | Trường hợp bình thường | Cần đọc lại DB ngay sau đó |

---

## Kết luận

- **Round-trip** = 1 chuyến đi-về giữa App và DB qua mạng.
- **Batch** = gom nhiều câu lệnh thành 1 chuyến đi-về duy nhất.
- `save()` cho phép Hibernate batch vì nó trì hoãn việc ghi → nhanh.
- `saveAndFlush()` ghi ngay lập tức → không batch được → chậm hơn khi có nhiều request.
