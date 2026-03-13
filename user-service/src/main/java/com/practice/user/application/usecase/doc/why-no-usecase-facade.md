```
╔══════════════════════════════════════════════════════════════════════════════════╗
║              TẠI SAO KHÔNG TẠO "USECASE TỔNG" (FACADE)                         ║
╚══════════════════════════════════════════════════════════════════════════════════╝


═══════════════════════════════════════════════════════════════════════════════════
 Ý TƯỞNG "UseCase Tổng" — UserUseCaseFacade
═══════════════════════════════════════════════════════════════════════════════════

 ❌ Cách làm sai:

 ┌─────────────────────────────────────────────────────┐
 │  class UserUseCaseFacade {                          │
 │      CreateUserUseCaseImpl createUser;              │
 │      UpdateUserUseCaseImpl updateUser;              │
 │      DeleteUserUseCaseImpl deleteUser;              │
 │      GetUserUseCaseImpl    getUser;                 │
 │                                                     │
 │      createUser(command) { return createUser... }   │
 │      updateUser(command) { return updateUser... }   │
 │      ...                                            │
 │  }                                                  │
 └─────────────────────────────────────────────────────┘

 Controller inject UserUseCaseFacade
 → thực ra chỉ là wrapper vô nghĩa
 → đây chính xác là UserService kiểu cũ, đổi tên thôi


═══════════════════════════════════════════════════════════════════════════════════
 VẤN ĐỀ KHI LÀM VẬY
═══════════════════════════════════════════════════════════════════════════════════

 [1] VI PHẠM Interface Segregation Principle (ISP)

     UserController chỉ cần createUser
     → nhưng phải inject cả Facade biết deleteUser, updateUser...
     → coupling không cần thiết

 [2] VI PHẠM Single Responsibility Principle (SRP)

     Thêm DeactivateUserUseCase → phải sửa Facade
     → một class thay đổi vì nhiều lý do khác nhau

 [3] MẤT ĐI lợi ích của 1 interface = 1 use case

     Hiện tại: Controller inject ICreateUserUseCase
     → Test: mock đúng 1 interface, không cần mock cả Facade
     → Trace: lỗi ở đâu → tìm đúng UseCase ngay

 [4] Facade chỉ delegate lại → thừa 1 layer vô nghĩa

     Controller → Facade.createUser()
                      → CreateUserUseCaseImpl.execute()
                              ↑
                         Facade không làm gì thêm


═══════════════════════════════════════════════════════════════════════════════════
 CÁCH ĐÚNG — GIỮ NGUYÊN PATTERN 1 INTERFACE = 1 USE CASE
═══════════════════════════════════════════════════════════════════════════════════

 Tương lai thêm use case:

  application/port/in/
  ├── ICreateUserUseCase.java      ← Controller POST /users inject cái này
  ├── IUpdateUserUseCase.java      ← Controller PUT /users/{id} inject cái này
  ├── IDeactivateUserUseCase.java  ← Controller DELETE inject cái này
  └── IGetUserUseCase.java         ← Controller GET inject cái này

  application/usecase/
  ├── CreateUserUseCaseImpl.java
  ├── UpdateUserUseCaseImpl.java
  ├── DeactivateUserUseCaseImpl.java
  └── GetUserUseCaseImpl.java

  UserController:
      inject ICreateUserUseCase     ← chỉ biết create
      inject IGetUserUseCase        ← chỉ biết get
      (không cần biết các use case khác tồn tại)


═══════════════════════════════════════════════════════════════════════════════════
 KHI NÀO MỚI CẦN FACADE / ORCHESTRATOR?
═══════════════════════════════════════════════════════════════════════════════════

 Chỉ cần khi:
 → 1 action của user cần GỌI NHIỀU use case theo thứ tự cụ thể
 → VÀ logic điều phối đó đủ phức tạp, cần test riêng

 Ví dụ: "Register + gửi welcome email + publish event"

 ┌─────────────────────────────────────────────────────┐
 │  class RegisterUserOrchestrator {                   │
 │      CreateUserUseCase    createUser;               │
 │      SendWelcomeEmail     sendEmail;                │
 │      PublishUserCreated   publishEvent;             │
 │                                                     │
 │      execute(command):                              │
 │          1. createUser.execute(command)             │
 │          2. sendEmail.send(user.email)              │
 │          3. publishEvent.publish(user.id)           │
 │  }                                                  │
 └─────────────────────────────────────────────────────┘

 → Đây là Saga / Orchestrator — có logic điều phối thực sự
 → KHÁC với Facade chỉ wrap và delegate lại


═══════════════════════════════════════════════════════════════════════════════════
 TÓM TẮT
═══════════════════════════════════════════════════════════════════════════════════

  ❌ UserUseCaseFacade    → wrapper vô nghĩa, vi phạm ISP + SRP
  ✅ 1 interface / 1 impl → inject đúng cái cần, test dễ, trace nhanh
  ✅ Orchestrator         → chỉ tạo khi có logic điều phối thực sự
```
