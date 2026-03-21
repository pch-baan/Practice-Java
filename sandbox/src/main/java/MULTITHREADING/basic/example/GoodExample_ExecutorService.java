package MULTITHREADING.basic.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * GOOD EXAMPLE: Dùng ExecutorService với Thread Pool
 *
 * Lợi ích:
 *  - Chỉ 5 threads dù có 20 orders → tiết kiệm tài nguyên
 *  - Thread được tái sử dụng, không tạo/hủy liên tục
 *  - Có thể lấy kết quả từng task qua Future
 *  - Biết chính xác khi nào tất cả task hoàn thành
 */
public class GoodExample_ExecutorService {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        System.out.println("=== GOOD: ExecutorService ===");

        // Chỉ 5 threads xử lý 20 orders
        ExecutorService executor = Executors.newFixedThreadPool(5);

        long start = System.currentTimeMillis();

        // Submit 20 tasks → nhận Future để lấy kết quả sau
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            final int orderId = i;
            Future<String> future = executor.submit(() -> processOrder(orderId));
            futures.add(future);
        }

        // Lấy kết quả từng task (get() sẽ block cho đến khi task xong)
        for (Future<String> future : futures) {
            System.out.println("Kết quả: " + future.get());
        }

        // Tắt pool — không nhận task mới, chờ task hiện tại xong
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long end = System.currentTimeMillis();
        System.out.printf("%nTổng thời gian: %d ms%n", end - start);
        System.out.println("=> Chỉ 5 threads, xử lý tuần tự theo pool, có kết quả rõ ràng!");
    }

    static String processOrder(int orderId) {
        System.out.printf("[%s] Xử lý order #%d bắt đầu%n",
                Thread.currentThread().getName(), orderId);
        try {
            Thread.sleep(500); // giả lập xử lý tốn 500ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return String.format("Order #%d hoàn thành bởi [%s]",
                orderId, Thread.currentThread().getName());
    }
}
