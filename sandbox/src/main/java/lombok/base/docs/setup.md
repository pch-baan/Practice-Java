# Lombok Setup — Cài đặt để dùng được

---

## Vấn đề hay gặp khi mới dùng Lombok

```
Code viết @Getter, mvn compile chạy OK ✅
Nhưng IDE (IntelliJ) gạch đỏ getName() ❌

→ Cannot find symbol: getName()
```

**Tại sao?** Lombok sinh code lúc compile — IntelliJ không biết điều đó nếu chưa cài plugin.

---

## Bước 1 — Thêm dependency vào `pom.xml`

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.30</version>
    <scope>provided</scope>   <!-- chỉ cần lúc compile, không cần lúc runtime -->
</dependency>
```

---

## Bước 2 — Cài Lombok plugin trong IntelliJ

```
IntelliJ IDEA
  → Settings (Cmd+,)
  → Plugins
  → Marketplace
  → Tìm "Lombok"
  → Install → Restart IDE
```

Sau khi cài: IDE hiểu `getName()` do `@Getter` sinh ra → hết gạch đỏ ✅

---

## Bước 3 — Bật Annotation Processing

```
IntelliJ IDEA
  → Settings (Cmd+,)
  → Build, Execution, Deployment
  → Compiler
  → Annotation Processors
  → ✅ Enable annotation processing
```

Nếu bỏ qua bước này → Lombok không chạy khi build trong IDE.

---

## Kiểm tra Lombok đã hoạt động chưa

```java
@Getter
public class TestLombok {
    private String name = "hello";

    public static void main(String[] args) {
        System.out.println(new TestLombok().getName()); // → hello
    }
}
```

- Chạy được, in ra `hello` → Lombok đã hoạt động ✅
- IDE không gạch đỏ `getName()` → Plugin đã cài đúng ✅
