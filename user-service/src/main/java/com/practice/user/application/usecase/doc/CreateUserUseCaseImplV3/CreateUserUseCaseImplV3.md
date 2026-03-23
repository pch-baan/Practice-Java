# CreateUserUseCaseImplV3 — Phân tích

## Vấn đề của V2

V2 vẫn gọi `validateUniqueConstraints()` trước mỗi INSERT:

```
[open connection]
  → existsByEmail()    ← SELECT 1
  → existsByUsername() ← SELECT 2
  → save(user)         ← INSERT 3
  → save(profile)      ← INSERT 4
[close connection]
```

3 round-trips chỉ để insert 1 user — lãng phí dưới high load.

---

## Pre-check là TOCTOU vulnerability

Dù có pre-check, race condition vẫn xảy ra:

```
Thread A: existsByEmail("x@gmail.com") → false ✓
Thread B: existsByEmail("x@gmail.com") → false ✓   ← cùng lúc
Thread A: INSERT x@gmail.com → OK
Thread B: INSERT x@gmail.com → DUPLICATE
```

Pre-check không tăng correctness — DB constraint mới là nguồn sự thật duy nhất.
Pre-check chỉ tốn thêm 2 SELECT mà không giải quyết được race condition.

---

## Fix trong V3: bỏ pre-check, dựa vào DB constraint

```
[open connection]
  → save(user)    ← INSERT 1
  → save(profile) ← INSERT 2
[close connection]
```

Từ 3 round-trips xuống còn 1.

### DB schema đã có sẵn unique constraint

```sql
CONSTRAINT uq_users_username UNIQUE (username),
CONSTRAINT uq_users_email    UNIQUE (email)
```

Khi email hoặc username trùng → DB ném `ConstraintViolationException`
→ `UserPostgresqlAdapter` bắt `DataIntegrityViolationException`
→ convert sang `UserConflictException` (domain exception)
→ tầng trên nhận đúng exception, không bị leak infrastructure detail.

---

## Tại sao adapter vẫn dùng saveAndFlush()?

`saveAndFlush()` flush ngay trong transaction → exception được bắt tại adapter,
convert sang `UserConflictException` trước khi bubble up.
Giữ clean DDD boundary: tầng UseCase không bao giờ thấy `DataIntegrityViolationException`.

---

## So sánh các version

| Version | BCrypt trong transaction | Pre-check SELECT | Round-trips/request | Vấn đề |
|---------|--------------------------|------------------|---------------------|--------|
| V1 | Có | Có (2 SELECT) | 3 | Connection held ~330ms → pool cạn > 160 req |
| V2 | Không | Có (2 SELECT) | 3 | 2 SELECT thừa mỗi request |
| V3 | Không | Không | 1 | — |

---

## Kết luận

V3 là cách production thực tế làm:
- Bỏ `existsByEmail()` và `existsByUsername()` pre-check
- Dựa vào DB unique constraint làm nguồn enforce
- Giữ `saveAndFlush()` + try-catch trong adapter để clean boundary
- Kết quả: 1 round-trip/request, an toàn race condition, đúng DDD boundary
