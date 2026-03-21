package lombok.base;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// Có Lombok — chỉ cần dán nhãn, tự sinh ra toàn bộ boilerplate ✨
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class UserLombok {

    private String name;
    private String email;
    private int age;

    public UserLombok(String name, String email, int age) {
        this.name = name;
        this.email = email;
        this.age = age;
    }

    // ── Demo ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        UserLombok u1 = new UserLombok("Alice", "alice@example.com", 25);
        UserLombok u2 = new UserLombok("Alice", "alice@example.com", 25);
        UserLombok u3 = new UserLombok("Bob",   "bob@example.com",   30);

        // toString — do @ToString sinh ra
        System.out.println(u1);
        // → UserLombok(name=Alice, email=alice@example.com, age=25)

        // equals — do @EqualsAndHashCode sinh ra
        System.out.println(u1.equals(u2)); // → true
        System.out.println(u1.equals(u3)); // → false

        // hashCode — do @EqualsAndHashCode sinh ra
        System.out.println(u1.hashCode() == u2.hashCode()); // → true
        System.out.println(u1.hashCode() == u3.hashCode()); // → false

        // getter/setter — do @Getter @Setter sinh ra
        System.out.println(u1.getName());  // → Alice
        u1.setName("Alice Updated");
        System.out.println(u1.getName());  // → Alice Updated
    }
}
