# Tại sao mọi class đều có `toString`, `equals`, `hashCode`?

**Một câu trả lời:** Vì mọi class Java đều ngầm kế thừa từ `Object`.

```
Bạn viết:          Java thực sự hiểu:

class Dog { }     class Dog extends Object { }
                       ↑
                   có sẵn toString(), equals(), hashCode()
```

---

## `Object` — "Tổ tiên" của mọi class

```
          Object
         /  |   \
      Dog  Car  String
      / \
  Poodle Husky
```

Dù bạn không viết `extends` gì — Java tự động gắn `extends Object`.

---

## 3 method đó làm gì mặc định?

| Method | Default behavior (trong `Object`) |
|---|---|
| `toString()` | `"Dog@1a2b3c"` — tên class + địa chỉ bộ nhớ |
| `equals(obj)` | So sánh **địa chỉ** (`==`), không so sánh nội dung |
| `hashCode()` | Trả về số dựa trên địa chỉ bộ nhớ |

---

## Demo nhanh — default behavior

```java
class Dog {
    String name;
    Dog(String name) { this.name = name; }
}

Dog a = new Dog("Rex");
Dog b = new Dog("Rex");

System.out.println(a.toString()); // Dog@6d06d69c  <- dia chi bo nho
System.out.println(a.equals(b));  // false  <- khac object, du cung ten
System.out.println(a.hashCode()); // 1895184  <- dua tren dia chi
```

---

## Tại sao phải **override** chúng?

```java
// Neu KHONG override equals:
Dog a = new Dog("Rex");
Dog b = new Dog("Rex");
a.equals(b); // false <- SAI ve mat business logic!

// Sau khi override:
@Override
public boolean equals(Object o) {
    Dog other = (Dog) o;
    return this.name.equals(other.name);
}
a.equals(b); // true <- dung roi
```

> **Rule ngam:** `equals` va `hashCode` phai **override cung nhau** — neu 2 object `equals` nhau thi phai co cung `hashCode`.
> Vi pham rule nay → `HashMap`, `HashSet` hoat dong sai.

---

## Tóm tắt

```
Moi class → extends Object (ngam dinh)
Object co san: toString / equals / hashCode
  └─ Default: so sanh dia chi bo nho, khong so sanh noi dung
  └─ Override khi: can so sanh theo business logic (ten, id, v.v.)
```

Lombok giai quyet chuyen nay bang `@EqualsAndHashCode` va `@ToString` — tu generate code cho ban.
