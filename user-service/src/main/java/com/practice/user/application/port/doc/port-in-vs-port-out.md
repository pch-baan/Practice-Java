```
╔══════════════════════════════════════════════════════════════════════════════════╗
║                    PORT/IN vs PORT/OUT — NHÀ HÀNG ANALOGY                      ║
╚══════════════════════════════════════════════════════════════════════════════════╝


  KHÁCH HÀNG          NHÀ HÀNG (Application)          NHÀ CUNG CẤP
  (Controller)            (UseCase)                   (DB / Email / ...)
       │                      │                              │
       │  "Tôi muốn gọi món"  │                              │
       │─────────────────────▶│                              │
       │                      │  "Lấy nguyên liệu cho tôi"  │
       │                      │─────────────────────────────▶│
       │                      │◀─────────────────────────────│
       │◀─────────────────────│                              │
       │  "Đây món của bạn"   │                              │


  ─── port/in ───▶  [  NHÀ HÀNG  ]  ─── port/out ───▶


═══════════════════════════════════════════════════════════════════════════════════
 PORT/IN — CỬA VÀO
═══════════════════════════════════════════════════════════════════════════════════

 Câu hỏi: AI đang GỌI vào application?
 Trả lời: Controller, Event listener, gRPC, Scheduler...

 port/in = interface mà APPLICATION EXPOSE ra ngoài
         = "Đây là những thứ tôi CÓ THỂ LÀM, hãy gọi tôi"

 ┌────────────────────────────────────────────┐
 │  interface ICreateUserUseCase {            │
 │      UserResponse execute(command);        │  ← application định nghĩa
 │  }                                         │
 │                                            │
 │  class CreateUserUseCaseImpl               │  ← application implement
 │      implements ICreateUserUseCase         │
 └────────────────────────────────────────────┘

  Controller chỉ biết interface ICreateUserUseCase
  → không biết Impl cụ thể là gì
  → đây là "driving port" — controller LÁI application


═══════════════════════════════════════════════════════════════════════════════════
 PORT/OUT — CỬA RA
═══════════════════════════════════════════════════════════════════════════════════

 Câu hỏi: Application/Domain đang GỌI RA đâu?
 Trả lời: Database, Email server, Message queue, External API...

 port/out = interface mà DOMAIN / APPLICATION YÊU CẦU từ bên ngoài
          = "Đây là những thứ tôi CẦN, ai đó hãy implement cho tôi"

 ┌────────────────────────────────────────────┐
 │  interface IUserRepository {               │
 │      User save(User user);                 │  ← domain định nghĩa
 │      boolean existsByEmail(EmailVO email); │
 │  }                                         │
 │                                            │
 │  class UserPersistenceAdapter              │  ← infrastructure implement
 │      implements IUserRepository            │
 └────────────────────────────────────────────┘

  Domain chỉ biết interface IUserRepository
  → không biết bên dưới là MySQL, PostgreSQL hay in-memory
  → đây là "driven port" — infrastructure BỊ LÁI bởi domain/application


═══════════════════════════════════════════════════════════════════════════════════
 INTERFACE THUỘC VỀ NGƯỜI DÙNG NÓ — KHÔNG PHẢI NGƯỜI IMPLEMENT
═══════════════════════════════════════════════════════════════════════════════════

 Nguyên tắc: Interface sống cùng layer với CALLER, không phải IMPLEMENTOR.

 ┌─────────────────────────────────────────────────────────────────────┐
 │  UserDomainService (domain/)                                        │
 │      userRepository.existsByEmail(email)  ← domain GỌI repository  │
 └─────────────────────────────────────────────────────────────────────┘

 → IUserRepository được dùng bởi domain/ (UserDomainService)
 → Nếu đặt IUserRepository trong application/port/out/ thì:

     domain/service/UserDomainService.java
         import com.practice.user.application.port.out.IUserRepository
                                   ↑
                              domain/ import application/ → VI PHẠM!

 → Vì vậy IUserRepository PHẢI sống trong domain/
   Đặt tên là domain/repository/ thay vì domain/port/out/
   → cùng ý nghĩa, nhưng tên trực quan hơn theo DDD tradition


═══════════════════════════════════════════════════════════════════════════════════
 KHI NÀO ĐẶT port/out TRONG domain/ vs application/?
═══════════════════════════════════════════════════════════════════════════════════

 ┌─────────────────────────┬───────────────────────────────────────────┐
 │  Ai là caller?          │  Interface đặt ở đâu?                     │
 ├─────────────────────────┼───────────────────────────────────────────┤
 │  domain/service/        │  domain/repository/  ← caller là domain   │
 │  application/usecase/   │  application/port/out/ ← caller là app    │
 └─────────────────────────┴───────────────────────────────────────────┘

 Ví dụ:
   IUserRepository   → domain gọi → domain/repository/
   IEventPublisher   → usecase gọi → application/port/out/
   IEmailSender      → usecase gọi → application/port/out/


═══════════════════════════════════════════════════════════════════════════════════
 TRICK GHI NHỚ
═══════════════════════════════════════════════════════════════════════════════════

  port/in  → NGƯỜI NGOÀI gọi VÀO application   (Controller → App)
  port/out → DOMAIN / APP gọi RA ngoài          (Domain/App → DB / API)

  IN  = ai drive application?           → Controller, Event, gRPC
  OUT = domain/application cần ai làm?  → DB, Email, Queue


═══════════════════════════════════════════════════════════════════════════════════
 TRONG PROJECT NÀY
═══════════════════════════════════════════════════════════════════════════════════

  application/port/in/
  └── ICreateUserUseCase.java     ← Controller gọi vào đây

  domain/repository/              ← port/out của domain (persistence)
  └── IUserRepository.java        ← UserDomainService + UseCase gọi ra đây


  application/port/out/           ← port/out của application layer
  └── (IEventPublisher, ...)      ← chỉ UseCase gọi, domain không cần biết


═══════════════════════════════════════════════════════════════════════════════════
 LUỒNG ĐẦY ĐỦ
═══════════════════════════════════════════════════════════════════════════════════

  interfaces/       port/in          application       domain/repo        infra
  Controller ─────▶ IUseCase ───────▶ UseCaseImpl ────▶ IRepository ─────▶ Adapter
                    (app định nghĩa) (app implement)   (domain định nghĩa) (infra implement)
                         ▲                                    ▲
                         │                                    │
                  Controller KHÔNG biết              UseCaseImpl KHÔNG biết
                  Impl là gì                         MySQL hay PostgreSQL
```
