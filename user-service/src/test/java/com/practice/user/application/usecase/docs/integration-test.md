# Integration Test là gì?

## Vấn đề: Unit Test không đủ

Unit test kiểm tra từng class **cô lập** — mock hết dependencies. Nhưng thực tế, code chạy qua **nhiều tầng cùng lúc**.

```
User gửi request
       ↓
   Controller
       ↓
   UseCase
       ↓
   Repository  ←── Unit test dừng ở đây, mock cái này
       ↓
   Database    ←── Integration test chạy đến tận đây
```

**Integration test** = kiểm tra rằng **nhiều thành phần làm việc đúng KHI KẾT HỢP với nhau**.

---

## Demo thực tế — Spring Boot

```java
// Unit Test: mock repository, không chạm DB
@Test
void createUser_unitTest() {
    when(userRepo.save(any())).thenReturn(mockUser);
    userService.createUser(request); // ← không bao giờ chạm DB thật
}
```

```java
// Integration Test: Spring context thật + DB thật (H2 hoặc Testcontainers)
@SpringBootTest
@Transactional
class CreateUserIntegrationTest {

    @Autowired
    private CreateUserUseCase createUserUseCase;

    @Autowired
    private UserJpaRepository userRepo;

    @Test
    void createUser_shouldPersistToDatabase() {
        // Act — chạy qua toàn bộ stack thật
        createUserUseCase.execute(new CreateUserCommand("hieu@test.com"));

        // Assert — kiểm tra DB thật
        Optional<UserJpaEntity> saved = userRepo.findByEmail("hieu@test.com");
        assertThat(saved).isPresent();
    }
}
```

---

## Bảng so sánh nhanh

| | Unit Test | Integration Test |
|---|---|---|
| **Kiểm tra** | 1 class cô lập | Nhiều layer kết hợp |
| **DB** | Mock | DB thật (H2 / Testcontainers) |
| **Spring context** | Không cần | `@SpringBootTest` |
| **Tốc độ** | Rất nhanh | Chậm hơn |
| **Bắt được lỗi** | Logic sai | Lỗi tích hợp (config sai, SQL sai, mapping sai...) |

---

## Trong project này

Project dùng kiến trúc Hexagonal — integration test thường viết ở:
- **Adapter layer**: kiểm tra `UserPostgresqlAdapter` + PostgreSQL thật
- **API layer**: kiểm tra full flow từ Controller → DB
