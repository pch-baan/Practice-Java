package lombok.base;

// Không có Lombok — phải viết tay toàn bộ boilerplate 😫
public class UserNormal {

    private String name;
    private String email;
    private int age;

    public UserNormal(String name, String email, int age) {
        this.name = name;
        this.email = email;
        this.age = age;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getName()  { return name; }
    public String getEmail() { return email; }
    public int getAge()      { return age; }

    // ── Setters ───────────────────────────────────────────────────────────────
    public void setName(String name)   { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setAge(int age)        { this.age = age; }

    // ── equals — ~10 dòng ────────────────────────────────────────────────────
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserNormal other = (UserNormal) o;
        return age == other.age
            && java.util.Objects.equals(name, other.name)
            && java.util.Objects.equals(email, other.email);
    }

    // ── hashCode — ~5 dòng ───────────────────────────────────────────────────
    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, email, age);
    }

    // ── toString — ~5 dòng ───────────────────────────────────────────────────
    @Override
    public String toString() {
        return "UserNormal{" +
            "name='" + name + '\'' +
            ", email='" + email + '\'' +
            ", age=" + age +
            '}';
    }

    // ── Demo ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        UserNormal u1 = new UserNormal("Alice", "alice@example.com", 25);
        UserNormal u2 = new UserNormal("Alice", "alice@example.com", 25);
        UserNormal u3 = new UserNormal("Bob",   "bob@example.com",   30);

        // toString
        System.out.println(u1);
        // → UserNormal{name='Alice', email='alice@example.com', age=25}

        // equals
        System.out.println(u1.equals(u2)); // → true  (cùng data)
        System.out.println(u1.equals(u3)); // → false (khác data)

        // hashCode — cùng data thì cùng hash
        System.out.println(u1.hashCode() == u2.hashCode()); // → true
        System.out.println(u1.hashCode() == u3.hashCode()); // → false
    }
}
