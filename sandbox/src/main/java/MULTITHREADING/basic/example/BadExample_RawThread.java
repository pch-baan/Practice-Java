package MULTITHREADING.basic.example;

import java.util.ArrayList;
import java.util.List;

/**
 * BAD EXAMPLE: Tạo new Thread() cho mỗi task
 *
 * Vấn đề:
 *  - 1000 request = 1000 threads được tạo ra cùng lúc
 *  - Mỗi thread tốn ~1MB stack → 1000 threads = ~1GB RAM
 *  - Tạo/hủy thread liên tục = tốn CPU (thread creation overhead)
 *  - Không có giới hạn → hệ thống sập khi traffic tăng đột biến
 *
 * Dùng join() để chờ đúng cách thay vì Thread.sleep() đoán mò,
 * nhưng vẫn cồng kềnh so với ExecutorService.
 */
public class BadExample_RawThread {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== BAD: Raw Thread (with join) ===");

        long start = System.currentTimeMillis();

        List<Thread> threads = new ArrayList<>();

        // Giả lập 20 orders đến cùng lúc (thực tế có thể là 10,000)
        for (int i = 0; i < 20; i++) {
            final int orderId = i + 1;
            Thread t = new Thread(() -> processOrder(orderId));
            t.start();
            threads.add(t);
        }

        // join() — block cho đến khi từng thread xong hẳn
        // Đúng hơn sleep(), nhưng vẫn phải tự quản lý từng thread thủ công
        for (Thread t : threads) {
            t.join();
        }

        long end = System.currentTimeMillis();
        System.out.printf("%nTổng thời gian: %d ms%n", end - start);
        System.out.println("=> 20 threads đã xong, nhưng vẫn tạo/hủy 20 threads!");
    }

    static void processOrder(int orderId) {
        System.out.printf("[%s] Xử lý order #%d bắt đầu%n",
                Thread.currentThread().getName(), orderId);
        try {
            Thread.sleep(1000); // giả lập xử lý tốn 500ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.printf("[%s] Xử lý order #%d XONG%n",
                Thread.currentThread().getName(), orderId);
    }
}
