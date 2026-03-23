# HashUtils — Hỏi & Đáp

## Câu hỏi 1: Có nên tạo util cho `sha256` không?

**Bối cảnh:** Phát hiện hàm `sha256` bị duplicate ở 5 file khác nhau:

- `LoginUseCaseImpl.java`
- `LogoutUseCaseImpl.java`
- `RegisterUseCaseImpl.java`
- `RefreshTokenUseCaseImpl.java`
- `VerifyEmailUseCaseImpl.java`

**Trả lời:** Nên tạo util. Đây là vi phạm nguyên tắc DRY (Don't Repeat Yourself) — cùng một đoạn code lặp lại ở 5 nơi, nếu cần sửa logic hashing phải sửa 5 chỗ, dễ sót.

---

## Câu hỏi 2: Đặt util ở layer nào?

**Bối cảnh:** Đề xuất ban đầu là `domain/service/HashUtils.java`.

**Sau khi check lại kiến trúc:**

Kiến trúc có quy tắc rõ ràng:

| Layer | Được import | Ghi chú |
|-------|-------------|---------|
| `domain/` | `java.*` only | Pure Java, không framework |
| `application/` | `domain/` + Spring | Điều phối luồng, chứa UseCase |
| `infrastructure/` | Tất cả | Chi tiết kỹ thuật |

Và một quyết định thiết kế quan trọng: **[1] 🚫 KHÔNG có commons module** — mỗi service hoàn toàn độc lập.

**Phân tích 2 lựa chọn:**

| Lựa chọn | Vị trí | Nhận xét |
|----------|--------|----------|
| A | `domain/service/HashUtils.java` | Pure Java ✅, nhưng `domain/service/` dành cho **business logic** (xem `AuthDomainService` chứa `validateUserCanLogin`, `validateRefreshToken`). SHA-256 là kỹ thuật, không phải domain. |
| B | `application/util/HashUtils.java` | 5 usecase dùng nó đều ở `application/usecase/` → cùng tầng ✅. Không vi phạm dependency rule ✅. Phản ánh đúng bản chất: đây là kỹ thuật phục vụ application layer. |

**Kết luận chọn B:** `application/util/HashUtils.java`

**Lý do cốt lõi:** SHA-256 ở đây là **kỹ thuật** (hash token trước khi lưu DB để bảo mật), không phải **nghiệp vụ domain**. Đặt vào `domain/service/` sẽ làm loãng ý nghĩa của layer domain.

---

## Kết quả refactor

**File mới tạo:**

```
application/util/HashUtils.java
```

```java
public final class HashUtils {
    private HashUtils() {}

    public static String sha256(String input) { ... }
}
```

**5 file được refactor** — xóa method `sha256()` nội bộ, thay bằng `HashUtils.sha256(...)`:

- `LoginUseCaseImpl.java`
- `LogoutUseCaseImpl.java`
- `RegisterUseCaseImpl.java`
- `RefreshTokenUseCaseImpl.java`
- `VerifyEmailUseCaseImpl.java`
