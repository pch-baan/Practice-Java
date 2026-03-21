package concurrency.bloom.l3.IdGeneration.production;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderServiceConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private DataSource dataSource;

    @Test
    void concurrent_200_orders_noIdDuplicates() throws InterruptedException, java.sql.SQLException {
        int threadCount = 200;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        List<Thread> threads = new ArrayList<>();
        CyclicBarrier startGun = new CyclicBarrier(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String customer = "C" + i;
            threads.add(new Thread(() -> {
                try {
                    startGun.await();
                    Order order = orderService.placeOrder(customer);
                    ids.add(order.getId());
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        threads.forEach(Thread::start);
        for (Thread t : threads) t.join();

        try (Connection conn = dataSource.getConnection()) {
            System.out.println("\n[PROOF] JDBC URL  : " + conn.getMetaData().getURL());
            System.out.println("[PROOF] DB product: " + conn.getMetaData().getDatabaseProductName());
            ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM orders");
            rs.next();
            System.out.println("[PROOF] Raw SQL COUNT(*) FROM orders = " + rs.getLong(1));
        }

        List<Order> allOrders = orderService.findAll();
        System.out.println("\n[DB QUERY] Orders in database: " + allOrders.size());
        System.out.println("[DB QUERY] ID range: " + allOrders.getFirst().getId() + " → " + allOrders.getLast().getId());
        allOrders.forEach(o -> System.out.printf("  Order #%-4d | customer: %-6s | createdAt: %s%n",
                o.getId(), o.getCustomerId(), o.getCreatedAt()));

        System.out.println("\n[PRODUCTION] " + ids.size() + "/200 orders placed");
        System.out.println("[PRODUCTION] " + ids.size() + " unique IDs — no duplicates. DB sequence is thread-safe by design.");

        assertThat(ids).hasSize(threadCount);
    }
}
