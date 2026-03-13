```
╔══════════════════════════════════════════════════════════════════════════════════╗
║          TẠI SAO KHÔNG CẦN application/service/ KHI ĐÃ CÓ usecase/             ║
╚══════════════════════════════════════════════════════════════════════════════════╝


═══════════════════════════════════════════════════════════════════════════════════
 VẤN ĐỀ: HAI CÁI TÊN — MỘT KHÁI NIỆM
═══════════════════════════════════════════════════════════════════════════════════

 Trong Clean Architecture / Hexagonal Architecture:

   "Application Service"  ===  "Use Case Interactor"

 Đây là cùng một thứ, chỉ khác tên gọi theo từng trường phái:

   Trường phái         │ Tên gọi
  ─────────────────────┼────────────────────────────────────────
   DDD (Evans)         │ Application Service
   Clean Architecture  │ Use Case / Interactor
   Hexagonal           │ Primary Port Implementation
  ─────────────────────┴────────────────────────────────────────

 → Tạo cả hai folder = tạo sự mơ hồ, không tạo thêm giá trị gì.


═══════════════════════════════════════════════════════════════════════════════════
 TRÁCH NHIỆM CỦA application/usecase/ TRONG PROJECT NÀY
═══════════════════════════════════════════════════════════════════════════════════

 CreateUserUseCaseImpl đang làm đúng mọi thứ mà một Application Service phải làm:

  ┌─────────────────────────────────────────────────────────────────────┐
  │  CreateUserUseCaseImpl.execute(command)                             │
  │                                                                     │
  │  1. Wrap String → VO         ← gọi domain object                  │
  │  2. validateUniqueConstraints ← gọi domain service                 │
  │  3. BCrypt(password)          ← technical concern (đúng chỗ)       │
  │  4. User.create(...)          ← gọi domain factory                 │
  │  5. userRepository.save()     ← gọi port/out                       │
  │  6. return UserResponseDto    ← map ra DTO                         │
  │                                                                     │
  │  @Transactional               ← quản lý transaction                │
  └─────────────────────────────────────────────────────────────────────┘

 Đây chính xác là định nghĩa của Application Service:
 → Điều phối luồng, không chứa business rule, biết transaction.


═══════════════════════════════════════════════════════════════════════════════════
 NẾU TẠO THÊM application/service/ — HẬU QUẢ
═══════════════════════════════════════════════════════════════════════════════════

 ❌ Câu hỏi không thể trả lời:
    "Logic này đặt trong service/ hay usecase/?"

 ❌ Team mới vào không biết khác nhau gì:
    UserService.java vs CreateUserUseCaseImpl.java

 ❌ Dễ bị lạm dụng — nhét business rule vào service/:
    if (user.getAge() < 18) throw ...   ← đây phải nằm trong domain!

 ❌ Nếu service/ chỉ gọi lại usecase/ → thừa một layer:

    Controller → Service → UseCase → Domain
                  ↑
               Chỉ là wrapper vô nghĩa


═══════════════════════════════════════════════════════════════════════════════════
 KHI NÀO MỚI CẦN application/service/?
═══════════════════════════════════════════════════════════════════════════════════

 Một số project dùng application/service/ thay vì application/usecase/.
 Đó là lựa chọn đặt tên — KHÔNG PHẢI hai thứ khác nhau.

 Chọn một trong hai, không dùng cả hai:

   Lựa chọn A (project này)       │ Lựa chọn B
  ─────────────────────────────────┼────────────────────────────────
   application/usecase/            │ application/service/
   CreateUserUseCaseImpl           │ UserApplicationService
   tên nói rõ use case cụ thể      │ tên nói rõ đây là service tầng app
  ─────────────────────────────────┴────────────────────────────────

 Project này chọn Lựa chọn A vì:
 → Tên UseCase thể hiện rõ từng hành động (SRP)
 → Dễ trace: một interface port/in ↔ một implementation
 → Phù hợp với Hexagonal: port/in là "use case" của hệ thống


═══════════════════════════════════════════════════════════════════════════════════
 TÓM TẮT
═══════════════════════════════════════════════════════════════════════════════════

   application/usecase/CreateUserUseCaseImpl.java
         │
         ├── Implements: domain/port/in/ICreateUserUseCase   ← port/in
         ├── Điều phối: DomainService + Repository + BCrypt
         ├── Quản lý: @Transactional
         └── Map: Command → Domain → ResponseDto

   → ĐÂY CHÍNH LÀ APPLICATION SERVICE

   application/service/ = folder trống = xóa đi
```
