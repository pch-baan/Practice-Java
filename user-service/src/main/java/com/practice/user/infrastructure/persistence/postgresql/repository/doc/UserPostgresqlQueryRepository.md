# UserPostgresqlQueryRepository — Mục đích & Hướng dẫn sử dụng

## Dùng để làm gì?

Viết các **native PostgreSQL query** — những câu query KHÔNG thể viết bằng JPQL hoặc Spring Data method naming.

## Ví dụ khi cần dùng

```java
// Full-text search với pg_trgm (fuzzy search)
@Query(value = "SELECT * FROM users WHERE username % :keyword ORDER BY similarity(username, :keyword) DESC",
       nativeQuery = true)
List<UserJpaEntity> searchByUsernameSimilarity(@Param("keyword") String keyword);

// JSONB query (nếu có cột jsonb)
@Query(value = "SELECT * FROM users WHERE metadata->>'source' = :source", nativeQuery = true)
List<UserJpaEntity> findByMetadataSource(@Param("source") String source);

// Native aggregation phức tạp
@Query(value = "SELECT DATE_TRUNC('day', created_at) as day, COUNT(*) FROM users GROUP BY 1", nativeQuery = true)
List<Object[]> countUsersPerDay();
```

## Tại sao tách riêng khỏi IUserJpaRepository?

| `IUserJpaRepository`           | `UserPostgresqlQueryRepository`     |
|--------------------------------|-------------------------------------|
| JPQL / Spring Data only        | Native SQL / PostgreSQL-specific    |
| Database-agnostic              | Gắn chặt với PostgreSQL             |
| Interface                      | Class (dùng `EntityManager`)        |

Tách ra để giữ `IUserJpaRepository` sạch và portable.
Nếu sau này đổi database → biết ngay chỗ nào cần sửa.

## Trạng thái hiện tại

Đang rỗng (placeholder). Thêm method vào đây khi cần native PostgreSQL query.
