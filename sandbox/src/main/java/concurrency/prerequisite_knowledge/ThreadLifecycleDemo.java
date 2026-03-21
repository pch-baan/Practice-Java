package concurrency.prerequisite_knowledge;

/**
 * Demo: Thread Lifecycle — quan sát 6 states bằng Thread.getState()
 *
 * Chạy trong IntelliJ: click nút Run bên cạnh main()
 * Chạy bằng Maven:
 *   mvn exec:java -pl sandbox -Dexec.mainClass="concurrency.prerequisite_knowledge.ThreadLifecycleDemo"
 */
public class ThreadLifecycleDemo {

    private static final Object LOCK = new Object();

    public static void main(String[] args) throws InterruptedException {

        // ─── STATE 1: NEW ───────────────────────────────────────────
        // Thread vừa được tạo, chưa start() — chưa làm gì cả
        Thread t = new Thread(() -> {});

        System.out.println("1. Sau new Thread()      → " + t.getState());
        // Output: NEW


        // ─── STATE 2: RUNNABLE ──────────────────────────────────────
        // Sau start(), JVM + OS quản lý thread
        // Có thể đang chạy HOẶC đang chờ CPU — không phân biệt được từ ngoài
        Thread busy = new Thread(() -> {
            long sum = 0;
            for (long i = 0; i < Long.MAX_VALUE; i++) sum += i; // loop vô tận
        });
        busy.setDaemon(true);
        busy.start();

        Thread.sleep(50); // nhường CPU để busy kịp start
        System.out.println("2. Đang loop vô tận      → " + busy.getState());
        // Output: RUNNABLE


        // ─── STATE 3: BLOCKED ───────────────────────────────────────
        // Thread A đang giữ lock, Thread B cố vào synchronized → bị chặn lại
        Thread holder = new Thread(() -> {
            synchronized (LOCK) {
                try { Thread.sleep(3000); } // giữ lock 3 giây
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });

        Thread waiter = new Thread(() -> {
            synchronized (LOCK) { /* muốn vào nhưng bị block */ }
        });

        holder.start();
        Thread.sleep(100); // chờ holder lấy được lock trước
        waiter.start();
        Thread.sleep(100); // chờ waiter thử vào và bị chặn

        System.out.println("3. Chờ lock bị giữ       → " + waiter.getState());
        // Output: BLOCKED


        // ─── STATE 4: WAITING ───────────────────────────────────────
        // Thread gọi wait() không có timeout → chờ đến khi có notify()
        Object signal = new Object();
        Thread waitingThread = new Thread(() -> {
            synchronized (signal) {
                try { signal.wait(); } // chờ vô thời hạn
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        waitingThread.start();
        Thread.sleep(100);

        System.out.println("4. Đang wait() vô hạn    → " + waitingThread.getState());
        // Output: WAITING

        synchronized (signal) { signal.notify(); } // đánh thức để thread không bị treo


        // ─── STATE 5: TIMED_WAITING ─────────────────────────────────
        // Thread gọi sleep(n) → chờ tối đa n milliseconds rồi tự dậy
        Thread sleeper = new Thread(() -> {
            try { Thread.sleep(5000); } // ngủ 5 giây
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        sleeper.start();
        Thread.sleep(100);

        System.out.println("5. Đang sleep(5000)      → " + sleeper.getState());
        // Output: TIMED_WAITING

        sleeper.interrupt(); // đánh thức sớm


        // ─── STATE 6: TERMINATED ────────────────────────────────────
        // Thread chạy xong run() — dù thành công hay exception
        Thread shortTask = new Thread(() -> {
            System.out.println("   [shortTask đang chạy...]");
        });
        shortTask.start();
        shortTask.join(); // chờ nó xong hẳn

        System.out.println("6. Sau khi join()         → " + shortTask.getState());
        // Output: TERMINATED
    }
}
