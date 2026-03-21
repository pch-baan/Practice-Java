# @Transactional — Partial Commit & ACID

## Câu chuyện mở đầu

Đăng ký tài khoản mới: **3 bước, 1 thao tác**.

```
Hùng bấm "Đăng ký":

Bước 1: INSERT users        → user_id = 42
Bước 2: INSERT user_profiles → profile của user 42
Bước 3: INSERT user_roles    → role "USER" cho user 42

Nếu Bước 2 fail, server crash, network timeout...
→ Bước 1 đã committed: user_id=42 tồn tại trong DB
→ Bước 2, 3 chưa chạy: không có profile, không có role

Kết quả: User "ma" — tồn tại nhưng không dùng được 💥 PARTIAL COMMIT!
```

---

## 🐛 Bug: `TransactionalBugDemo.java`

### Pattern sai — không có @Transactional

```java
public void registerUserBuggy(String name) {
    User user = userRepo.save(new User(name));    // Bước 1: COMMIT ngay
    // ↑ nếu crash ở đây → User tồn tại, Profile không

    profileRepo.save(new UserProfile(user.getId())); // Bước 2: COMMIT ngay
    // ↑ nếu crash ở đây → User + Profile tồn tại, Role không

    roleRepo.assign(user.getId(), "USER");           // Bước 3: COMMIT ngay
}
```

Mỗi `save()` là 1 commit riêng lẻ — không atomic.

### Minh họa partial commit

```
Thời gian →→→→→→→→→→→→→→→→→→→→→→→→→→→

Bước 1: [COMMIT: user saved]
Bước 2:                      [CRASH / EXCEPTION]
Bước 3:                                          (không chạy)

DB state:
  users         : [user_id=42, name="Hung"]  ← đã commit
  user_profiles : []                          ← chưa có
  user_roles    : []                          ← chưa có

→ DATA INCONSISTENCY!
```

---

## ✅ Fix: @Transactional

### Cơ chế hoạt động

```java
@Transactional
public void registerUser(String name) {
    User user = userRepo.save(new User(name));        // Bước 1: chưa commit
    profileRepo.save(new UserProfile(user.getId()));  // Bước 2: chưa commit
    roleRepo.assign(user.getId(), "USER");             // Bước 3: chưa commit
}   // ← method kết thúc → Spring commit TẤT CẢ cùng lúc
    // ← nếu exception bất kỳ → Spring ROLLBACK TẤT CẢ
```

Spring không commit từng `save()` ngay lập tức.
Tất cả thao tác được gom vào 1 transaction, commit 1 lần ở cuối.

### Flow đầy đủ — AOP Proxy

```
Caller gọi registerUser() ──► Spring AOP Proxy
                                     │
                              BEGIN TRANSACTION
                                     │
                              ┌──────┴──────────────┐
                              │  actual method runs  │
                              │  save(user)          │  ← chỉ pending trong Hibernate session
                              │  save(profile)       │  ← chỉ pending
                              │  assign(role)        │  ← chỉ pending
                              └──────┬──────────────┘
                                     │
                            exception xảy ra?
                           ┌──────────┴──────────┐
                          YES                    NO
                           │                     │
                       ROLLBACK               COMMIT
                    (tất cả bị hủy)      (tất cả được ghi)
                           │                     │
                    caller nhận           caller nhận
                    exception             return value
```

### SQL thực tế

```sql
-- Spring BEGIN:
BEGIN;

-- Hibernate "flush" khi commit:
INSERT INTO users (name) VALUES ('Hung') RETURNING id;        -- id=42
INSERT INTO user_profiles (user_id) VALUES (42);
INSERT INTO user_roles (user_id, role) VALUES (42, 'USER');

-- Spring COMMIT (tất cả hoặc không gì cả):
COMMIT;

-- Nếu exception:
ROLLBACK;  -- 3 INSERT đều bị hủy
```

---

## 🔍 Output thực tế

### Trường hợp BUG (không có @Transactional)

```
[BUG] User saved  : id=1, name=Hung
[BUG] Exception   : Network timeout while creating profile!
[BUG] Users       : {1=Hung}   ← đã commit
[BUG] Profiles    : {}         ← chưa có
[BUG] User without profile? true  ← BUG confirmed!
```

### Trường hợp FIX (@Transactional)

```
[FIX] User pending    : id=2, name=Hung
[FIX] ROLLBACK        : Network timeout while creating profile!
[FIX] Users           : {}  ← không có gì
[FIX] Profiles        : {}  ← không có gì
[FIX] Data consistent? true  ← ✅
```

---

## ⚙️ Propagation — @Transactional lồng nhau

Khi method A (có @Transactional) gọi method B (cũng có @Transactional):

```java
@Transactional                          // Transaction A bắt đầu
public void registerUser(...) {
    userRepo.save(user);
    notificationService.sendWelcome();  // gọi vào method B
}

@Transactional                          // propagation mặc định: REQUIRED
public void sendWelcome() {             // ← tham gia Transaction A (không tạo mới)
    ...
}
```

| Propagation | Hành vi |
|---|---|
| `REQUIRED` *(default)* | Tham gia transaction hiện có, tạo mới nếu chưa có |
| `REQUIRES_NEW` | Luôn tạo transaction mới, suspend transaction cũ |
| `NESTED` | Transaction con, rollback độc lập với cha |
| `NOT_SUPPORTED` | Suspend transaction, chạy ngoài transaction |
| `NEVER` | Throw exception nếu đang có transaction |

### Ví dụ REQUIRES_NEW

```java
@Transactional
public void registerUser(String name) {
    userRepo.save(user);
    auditService.log("registered " + name);  // REQUIRES_NEW: luôn commit, dù registerUser rollback
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void log(String message) {
    auditRepo.save(new AuditLog(message));  // ← commit riêng biệt
}
```

```
Transaction A (registerUser):  BEGIN ──────────────────── ROLLBACK
Transaction B (log):                    BEGIN ── COMMIT
                                              ↑ độc lập với A
```

Dùng khi: audit log, notification — muốn ghi lại dù main transaction fail.

---

## 🔁 Rollback Rules

Mặc định `@Transactional` chỉ rollback với **unchecked exception** (`RuntimeException`, `Error`).

```java
@Transactional
public void doSomething() throws IOException {
    repo.save(entity);
    throw new IOException("checked");  // ← KHÔNG rollback mặc định!
}

// Fix:
@Transactional(rollbackFor = IOException.class)
public void doSomething() throws IOException {
    repo.save(entity);
    throw new IOException("checked");  // ← rollback ✅
}
```

| Exception type | Rollback mặc định |
|---|---|
| `RuntimeException` và subclass | ✅ Có |
| `Error` | ✅ Có |
| `Exception` (checked) | ❌ Không (phải khai báo `rollbackFor`) |

---

## ⚠️ Pitfall — Self-invocation không hoạt động

```java
@Service
public class UserService {

    public void register(String name) {
        this.registerInternal(name);   // ← gọi trực tiếp trong cùng class
    }

    @Transactional                     // ← KHÔNG có effect!
    public void registerInternal(String name) {
        // @Transactional bị bỏ qua vì gọi qua 'this', không qua proxy
    }
}
```

```
Caller ──► Spring Proxy ──► register() ──► this.registerInternal()
                ↑                                    ↑
           proxy active                       bypass proxy!
                                          @Transactional bị ignore
```

**Fix**: inject self hoặc tách thành service riêng.

---

## 📊 Tổng kết

### Khi nào dùng @Transactional?

| Tình huống | Dùng @Transactional? |
|---|---|
| 1 câu SELECT | Không cần |
| 1 câu INSERT/UPDATE đơn lẻ | Thường không cần |
| **2+ thao tác DB phải đi cùng nhau** | **Cần** ✅ |
| Đọc rồi ghi dựa trên kết quả đọc | Cần (tránh stale read) |
| Audit log độc lập | `REQUIRES_NEW` |

### Câu hỏi tự kiểm tra

> *"Nếu bước giữa chừng bị fail, data có bị lệch không?"*
>
> - **Có** → dùng `@Transactional`
> - **Không** → không cần

### Quy tắc vàng

> `@Transactional` không phải chỉ cho tiền.
> Bất cứ khi nào **nhiều thao tác DB phải thành công hoặc thất bại cùng nhau**,
> đó là lúc cần `@Transactional`.
