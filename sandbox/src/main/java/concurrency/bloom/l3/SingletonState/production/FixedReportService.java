package concurrency.bloom.l3.SingletonState.production;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ✅ FIXED — Dùng khi shared queue là thiết kế có chủ ý.
 * (Ví dụ: background processor gom nhiều request lại rồi xử lý batch)
 *
 * Fix: thay ArrayList → ConcurrentLinkedQueue
 *   - offer() và poll() đều thread-safe, non-blocking
 *   - Không cần synchronized, không bottleneck
 *
 * Lưu ý: Nếu mỗi user CẦN data riêng biệt (không muốn share),
 * thì đây KHÔNG phải fix đúng — bạn cần thiết kế stateless:
 *   - Store state trong DB, Redis, hoặc request parameter
 *   - Dùng @RequestScope nếu cần bean per-request trong Spring
 */
@Service
@Slf4j
public class FixedReportService {

    // ✅ Thread-safe queue — đúng khi shared queue là intent
    private final Queue<String> pendingReports = new ConcurrentLinkedQueue<>();

    public void submit(String report) {
        pendingReports.offer(report);   // thread-safe, non-blocking
        log.info("[PRODUCTION] Report queued: {}", report);
    }

    public int count() {
        return pendingReports.size();
    }

    public String poll() {
        return pendingReports.poll();   // lấy và xóa phần tử đầu queue
    }
}
