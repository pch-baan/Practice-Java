package annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bug Demo: Partial Commit — không có @Transactional
 *
 * Kịch bản: Đăng ký user cần 2 bước:
 *   Bước 1: INSERT vào bảng users
 *   Bước 2: INSERT vào bảng user_profiles
 *
 * Nếu Bước 2 fail mà không có @Transactional:
 *   → User đã được lưu vào DB (Bước 1 committed rồi)
 *   → Profile chưa được lưu (Bước 2 chưa chạy)
 *   → DATA INCONSISTENCY: user tồn tại nhưng không có profile
 *
 * Fix: @Transactional — xem UserRegistrationService.java trong production/
 */
public class TransactionalBugDemo {

    // Simulate "database" tables (in-memory)
    static Map<Long, String> userTable = new HashMap<>();
    static Map<Long, Long> profileTable = new HashMap<>();
    static AtomicLong idGen = new AtomicLong(1);

    /**
     * BUG: không có @Transactional.
     * Mỗi save() là 1 commit riêng lẻ — không atomic.
     */
    static void registerUserBuggy(String name, boolean failOnProfile) {
        long userId = idGen.getAndIncrement();

        // Bước 1: Save user — commit ngay lập tức
        userTable.put(userId, name);
        System.out.println("[BUG] User saved  : id=" + userId + ", name=" + name);

        if (failOnProfile) {
            // Bước 2 chưa chạy, nhưng Bước 1 đã committed → dữ liệu lệch
            throw new RuntimeException("Network timeout while creating profile!");
        }

        // Bước 2: Save profile
        profileTable.put(userId, userId);
        System.out.println("[BUG] Profile saved: userId=" + userId);
    }

    /**
     * FIX (mô phỏng @Transactional): rollback Bước 1 nếu Bước 2 fail.
     * Trong thực tế Spring tự làm điều này — đây chỉ là minh họa cơ chế.
     */
    static void registerUserFixed(String name, boolean failOnProfile) {
        long userId = idGen.getAndIncrement();

        // Simulate: các thao tác chưa commit, chỉ "pending"
        Map<Long, String> pendingUsers = new HashMap<>(userTable);
        Map<Long, Long> pendingProfiles = new HashMap<>(profileTable);

        try {
            pendingUsers.put(userId, name);
            System.out.println("[FIX] User pending : id=" + userId + ", name=" + name);

            if (failOnProfile) {
                throw new RuntimeException("Network timeout while creating profile!");
            }

            pendingProfiles.put(userId, userId);
            System.out.println("[FIX] Profile pending: userId=" + userId);

            // COMMIT: tất cả hoặc không gì cả
            userTable.putAll(pendingUsers);
            profileTable.putAll(pendingProfiles);
            System.out.println("[FIX] COMMIT: both user and profile saved.");

        } catch (Exception e) {
            // ROLLBACK: không ghi gì vào DB
            System.out.println("[FIX] ROLLBACK: " + e.getMessage());
            // pendingUsers và pendingProfiles bị bỏ đi — userTable/profileTable không đổi
        }
    }

    public static void main(String[] args) {
        System.out.println("=== BUG: Partial Commit (không có @Transactional) ===");
        try {
            registerUserBuggy("Hung", true);  // profile creation fails
        } catch (Exception e) {
            System.out.println("[BUG] Exception: " + e.getMessage());
        }
        System.out.println("[BUG] Users    : " + userTable);
        System.out.println("[BUG] Profiles : " + profileTable);
        boolean orphanUser = userTable.size() > profileTable.size();
        System.out.println("[BUG] User without profile? " + orphanUser + "  ← should be false, BUG confirmed!");

        System.out.println();
        userTable.clear();
        profileTable.clear();

        System.out.println("=== FIX: @Transactional (all-or-nothing) ===");
        registerUserFixed("Hung", true);   // profile creation fails → ROLLBACK
        System.out.println("[FIX] Users    : " + userTable);
        System.out.println("[FIX] Profiles : " + profileTable);
        System.out.println("[FIX] Data consistent? " + (userTable.size() == profileTable.size()) + "  ← ✅");
    }
}
