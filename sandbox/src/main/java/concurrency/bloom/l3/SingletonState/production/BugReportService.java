package concurrency.bloom.l3.SingletonState.production;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ❌ WRONG — Đừng làm thế này trong production.
 *
 * @Service là singleton: Spring tạo đúng 1 instance cho toàn bộ app.
 * ArrayList này được SHARE giữa mọi thread/request.
 *
 * 2 vấn đề:
 *   1. Thread-safety: ArrayList.add() không atomic → data corruption
 *   2. Data isolation: Mọi user đều nhìn thấy report của nhau
 *
 * Xem FixedReportService.java để biết cách fix.
 */
@Service
public class BugReportService {

    // ❌ Mutable instance field trong singleton = shared state = trouble
    private final List<String> pendingReports = new ArrayList<>();

    public void submit(String report) {
        pendingReports.add(report);   // NOT thread-safe!
    }

    public int count() {
        return pendingReports.size();
    }

    public List<String> getAll() {
        return pendingReports;
    }
}
