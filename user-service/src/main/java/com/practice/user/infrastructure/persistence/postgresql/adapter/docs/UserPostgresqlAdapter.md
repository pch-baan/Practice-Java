# UserPostgresqlAdapter

## Mục đích

`UserPostgresqlAdapter` là phần **hiện thực (implementation)** của `IUserRepository` — tức là "người thực thi hợp đồng" mà domain đã ký.

Class này nằm ở tầng **Infrastructure**, làm nhiệm vụ **dịch ngôn ngữ** giữa domain và PostgreSQL.

```
UseCase (tầng domain)
     │ gọi IUserRepository
     ▼
UserPostgresqlAdapter   ← class này
     │
     ├─ dùng IUserJpaRepository  (Spring Data JPA → PostgreSQL)
     └─ dùng UserPersistenceMapper (chuyển đổi domain ↔ JPA entity)
```

## Luồng xử lý của method `save()` — ví dụ điển hình

```
UseCase gọi: repository.save(user)
                │
                ▼
        [1] mapper.toJpaEntity(user)
            Domain User → UserJpaEntity
                │
                ▼
        [2] userJpaRepository.save(entity)
            Spring Data JPA → INSERT/UPDATE vào PostgreSQL
                │
                ▼
        [3] mapper.toDomain(savedEntity)
            UserJpaEntity → Domain User (có ID mới từ DB)
                │
                ▼
        Trả về Domain User cho UseCase
```

Các method còn lại (`findById`, `findByUsername`, v.v.) cũng theo mô hình tương tự:
**nhận domain object → gọi JPA → trả về domain object**.

## Tại sao cần class này?

| Vấn đề | Giải pháp |
|---|---|
| Domain không biết JPA | Adapter đứng giữa, dịch qua lại |
| `IUserRepository` chỉ là interface | Class này implement để Spring inject vào UseCase |
| Domain dùng Value Object (`EmailVO`, `UsernameVO`) | Adapter gọi `.getValue()` để lấy raw string trước khi truyền vào JPA |

**Domain không biết PostgreSQL tồn tại. `UserPostgresqlAdapter` là người duy nhất biết điều đó.**
