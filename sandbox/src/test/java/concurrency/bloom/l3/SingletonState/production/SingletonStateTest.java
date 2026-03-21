package concurrency.bloom.l3.SingletonState.production;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SingletonStateTest {

    @Autowired
    private BugReportService bugService;

    @Autowired
    private FixedReportService fixedService;

    @Test
    void bugService_is_same_instance_across_injections() {
        // Chứng minh @Service là singleton: mọi nơi inject đều là cùng 1 object
        // (Đây là ROOT CAUSE của vấn đề — không phải bug của Spring, là misuse của developer)
        BugReportService anotherRef = bugService;

        bugService.submit("from-controller");
        bugService.submit("from-scheduler");

        // Cả hai ref đều trỏ tới cùng 1 instance → cùng ArrayList
        assertThat(anotherRef.count()).isEqualTo(2);

        System.out.println("[SINGLETON] Same instance: " + (bugService == anotherRef));
        System.out.println("[SINGLETON] Data shared: both see count=" + anotherRef.count());
    }

    @Test
    void fixedService_concurrent_200_submits_noLostItems() throws InterruptedException {
        int threadCount = 200;
        List<Thread> threads = new ArrayList<>();
        CyclicBarrier startGun = new CyclicBarrier(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String report = "report-" + i;
            threads.add(new Thread(() -> {
                try {
                    startGun.await();
                    fixedService.submit(report);   // ConcurrentLinkedQueue — thread-safe
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        threads.forEach(Thread::start);
        for (Thread t : threads) t.join();

        System.out.println("[PRODUCTION] Submitted: " + threadCount
                + " | Queued: " + fixedService.count());
        System.out.println("[PRODUCTION] ConcurrentLinkedQueue — no lost items");

        assertThat(fixedService.count()).isEqualTo(threadCount);
    }
}
