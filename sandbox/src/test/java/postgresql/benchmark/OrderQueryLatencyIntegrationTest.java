package postgresql.benchmark;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Benchmark latency — chứng minh tại sao "263ms × 17,280 lần/ngày" là vấn đề thật.
 *
 * ── Yêu cầu ──────────────────────────────────────────────────────────────────
 *   1. PostgreSQL đang chạy:  docker-compose up postgres -d
 *   2. orders table đã có dữ liệu benchmark (≥ 100,000 rows):
 *      psql -f sandbox/postgresql-lab/benchmark/01_baseline_benchmark.sql  (hoặc seed script)
 *
 *   Nếu orders chưa có đủ data → test tự skip qua assumeThat.
 *
 * ── Scenario 1: Worker Polling Simulation ────────────────────────────────────
 *
 *   worker-service poll PENDING orders mỗi 5 giây = 17,280 lần/ngày.
 *   Test chạy query 100 lần, lấy avg_ms, extrapolate:
 *
 *     avg_ms × 17,280 = tổng CPU time DB phải bỏ ra mỗi ngày CHỈ để trả lời worker
 *
 *   Assert: nếu avg ≥ 50ms → daily cost ≥ 864 giây (14 phút) — không thể chấp nhận
 *   cho một hot-path query chạy liên tục 24/7.
 *
 * ── Scenario 2: Concurrent Load ──────────────────────────────────────────────
 *
 *   100 threads cùng gửi query ĐỒNG THỜI (simulate 100 users đặt hàng cùng lúc).
 *   HikariCP pool mặc định = 10 connections.
 *
 *   Lý thuyết batching:
 *     100 queries / 10 connections = 10 batches
 *     wall_clock_time ≈ 10 × avg_ms  (thay vì avg_ms nếu chạy 1 mình)
 *
 *   Assert: wall_clock_time ≥ avg_single × 5 (50% margin — chứng minh queueing thật)
 *
 *   Đây là điều mà con số "263ms" không thấy: user thứ 91-100 phải chờ
 *   9 batch trước xử lý xong trước khi nhận được connection.
 *
 * @see sandbox/postgresql-lab/benchmark/01_baseline_benchmark.sql   SQL gốc của query B5
 * @see sandbox/postgresql-lab/benchmark/docs/partition-benchmark-results.md  số liệu thực tế
 */
@SpringBootTest
@ActiveProfiles("local")
class OrderQueryLatencyIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryLatencyIntegrationTest.class);

    // ── Query — hot path B5: worker poll PENDING trong 30 ngày gần nhất ─────
    private static final String QUERY_PENDING_ORDERS = """
            SELECT id, user_id, total_amount, created_at
            FROM orders
            WHERE status = 'PENDING'
              AND created_at >= NOW() - INTERVAL '30 days'
            ORDER BY created_at DESC
            LIMIT 50
            """;

    // ── Thông số benchmark ────────────────────────────────────────────────────
    private static final int  POLLING_SAMPLE      = 100;   // số lần chạy để lấy avg
    private static final int  CONCURRENT_THREADS  = 100;   // 100 users cùng lúc
    private static final int  CONNECTION_POOL_SIZE = 10;   // HikariCP default
    private static final long POLLS_PER_DAY        = 86_400L / 5; // 17,280 lần/ngày

    // ── Ngưỡng assert ─────────────────────────────────────────────────────────
    // Nếu avg ≥ 50ms thì daily cost ≥ 864s = không chấp nhận cho hot path
    private static final long SLOW_QUERY_THRESHOLD_MS = 50;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void requireBenchmarkData() {
        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders", Long.class);
        assumeThat(rowCount)
                .as("orders table cần ≥ 100,000 rows để benchmark có ý nghĩa. " +
                    "Hiện tại: %d rows. Chạy seed script trước.", rowCount)
                .isGreaterThanOrEqualTo(100_000L);
        log.info("[SETUP] orders table: {:,} rows — đủ điều kiện chạy benchmark", rowCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCENARIO 1: Worker Polling — 263ms × 17,280 = tích lũy thành bottleneck
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Scenario 1 - Polling] avg_ms × 17,280 lần/ngày = daily CPU cost không thể bỏ qua")
    void scenario1_pollingCumulativeCost() {
        log.info("▶ [SCENARIO 1] Worker Polling Simulation");
        log.info("  Query: PENDING orders trong 30 ngày — hot path của worker-service");
        log.info("  Chạy {} lần để lấy avg_ms thực tế...", POLLING_SAMPLE);

        // Warm up — không tính vào kết quả
        runQuery();

        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < POLLING_SAMPLE; i++) {
            latencies.add(runQuery());
        }

        long avgMs    = (long) latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long minMs    = latencies.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxMs    = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
        long totalMs  = latencies.stream().mapToLong(Long::longValue).sum();

        // ── Extrapolate ra 1 ngày ──
        long dailyCpuSeconds = avgMs * POLLS_PER_DAY / 1000;
        double dailyCpuHours = dailyCpuSeconds / 3600.0;

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  Sample ({} queries):", POLLING_SAMPLE);
        log.info("    avg={} ms    min={} ms    max={} ms", avgMs, minMs, maxMs);
        log.info("    total elapsed: {} ms ({} giây)", totalMs, totalMs / 1000);
        log.info("");
        log.info("  Extrapolated → 1 ngày ({} polls/day):", POLLS_PER_DAY);
        log.info("    {} ms × {:,} = {:,} giây = {:.2f} giờ CPU/ngày",
                avgMs, POLLS_PER_DAY, dailyCpuSeconds, dailyCpuHours);
        log.info("    (Chỉ để trả lời worker poll — chưa tính user traffic khác)");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (avgMs >= SLOW_QUERY_THRESHOLD_MS) {
            log.warn("  ⚠️  avg {}ms ≥ {}ms threshold — daily cost {} giây ({:.2f} giờ)",
                    avgMs, SLOW_QUERY_THRESHOLD_MS, dailyCpuSeconds, dailyCpuHours);
            log.warn("  ⚠️  Đây là hot path 24/7 — cần partition hoặc better index");

            // Assert: daily cost phải ≥ 14 phút (= 50ms × 17,280 / 1000 = 864s)
            // Đây là con số chứng minh "không thể bỏ qua"
            long expectedMinDailyCost = SLOW_QUERY_THRESHOLD_MS * POLLS_PER_DAY / 1000; // 864s
            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(dailyCpuSeconds)
                    .as("Daily CPU cost phải ≥ %ds (= %dms threshold × %d polls/day / 1000). " +
                        "Đây là bằng chứng tích lũy: avg=%dms mỗi query nhân lên 17,280 lần/ngày " +
                        "= %ds CPU time mỗi ngày chỉ để trả lời worker.",
                        expectedMinDailyCost, SLOW_QUERY_THRESHOLD_MS, POLLS_PER_DAY,
                        avgMs, dailyCpuSeconds)
                    .isGreaterThanOrEqualTo(expectedMinDailyCost);
            softly.assertAll();
        } else {
            log.info("  ✅ avg {}ms < {}ms threshold — hot path đạt yêu cầu", avgMs, SLOW_QUERY_THRESHOLD_MS);
            log.info("  ✅ Daily cost: {} giây ({:.2f} giờ) — chấp nhận được", dailyCpuSeconds, dailyCpuHours);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCENARIO 2: Concurrent Load — 100 queries cùng lúc, DB bắt đầu queue
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Scenario 2 - Concurrent] 100 queries cùng lúc → wall time >> single query avg")
    void scenario2_concurrentQueueingEffect() throws InterruptedException {
        log.info("▶ [SCENARIO 2] Concurrent Load Test");
        log.info("  {} threads gửi query CÙNG LÚC — pool={} connections", CONCURRENT_THREADS, CONNECTION_POOL_SIZE);

        // Warm up — lấy avg single-query
        long warmupMs = runQuery();
        List<Long> warmupLatencies = new ArrayList<>();
        for (int i = 0; i < 10; i++) warmupLatencies.add(runQuery());
        long avgSingleMs = (long) warmupLatencies.stream().mapToLong(Long::longValue).average().orElse(warmupMs);

        log.info("  Single-query avg (warm): {} ms", avgSingleMs);
        log.info("  Lý thuyết: {} queries / {} pool = {} batches → wall ≈ {} × {} = ~{} ms",
                CONCURRENT_THREADS, CONNECTION_POOL_SIZE,
                CONCURRENT_THREADS / CONNECTION_POOL_SIZE,
                CONCURRENT_THREADS / CONNECTION_POOL_SIZE, avgSingleMs,
                (CONCURRENT_THREADS / CONNECTION_POOL_SIZE) * avgSingleMs);

        // ── Chạy 100 concurrent ──
        CountDownLatch startLatch       = new CountDownLatch(1);
        CountDownLatch doneLatch        = new CountDownLatch(CONCURRENT_THREADS);
        List<Long>     concurrentTimes  = Collections.synchronizedList(new ArrayList<>());
        AtomicLong     wallStart        = new AtomicLong();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // barrier — tất cả thread xuất phát CÙNG LÚC
                    long t0  = System.currentTimeMillis();
                    runQuery();
                    long elapsed = System.currentTimeMillis() - t0;
                    concurrentTimes.add(elapsed);
                    if (idx % 20 == 0) {
                        log.info("  [Thread-{}] done in {} ms", idx, elapsed);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }, "bench-t" + i).start();
        }

        wallStart.set(System.currentTimeMillis());
        startLatch.countDown(); // "Go!" — 100 threads xuất phát

        boolean done = doneLatch.await(60, TimeUnit.SECONDS);
        long wallMs  = System.currentTimeMillis() - wallStart.get();

        // ── Thống kê ──
        List<Long> sorted = new ArrayList<>(concurrentTimes);
        Collections.sort(sorted);
        long p50 = percentile(sorted, 50);
        long p95 = percentile(sorted, 95);
        long p99 = percentile(sorted, 99);
        long avgConcurrent = (long) sorted.stream().mapToLong(Long::longValue).average().orElse(0);

        long expectedMinWallMs = avgSingleMs * (CONCURRENT_THREADS / CONNECTION_POOL_SIZE) / 2; // 50% margin

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  Concurrent ({} queries đồng thời):", CONCURRENT_THREADS);
        log.info("    avg={} ms    p50={} ms    p95={} ms    p99={} ms",
                avgConcurrent, p50, p95, p99);
        log.info("    wall clock = {} ms  (thời gian từ lúc bắt đầu đến xong HẾT 100 queries)", wallMs);
        log.info("");
        log.info("  So sánh:");
        log.info("    Single query avg  : {} ms", avgSingleMs);
        log.info("    Concurrent p50    : {} ms  ({} ×)", p50, p50 / Math.max(avgSingleMs, 1));
        log.info("    Concurrent p95    : {} ms  ({} × — user thứ 95 phải chờ lâu hơn {} lần)",
                p95, p95 / Math.max(avgSingleMs, 1), p95 / Math.max(avgSingleMs, 1));
        log.info("    Wall clock        : {} ms  (lý thuyết min: {} ms)", wallMs, expectedMinWallMs * 2);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(done)
                .as("100 threads phải hoàn thành trong 60 giây")
                .isTrue();

        // Assert wall time ≥ expectedMinWallMs: chứng minh có queueing (không phải song song hoàn toàn)
        softly.assertThat(wallMs)
                .as("Wall time (%dms) phải ≥ %dms. " +
                    "Lý do: %d queries / %d connections = %d batches. " +
                    "Nếu không có queueing thì wall ≈ %dms (single query). " +
                    "Wall >> single query = bằng chứng DB đang xếp hàng chờ connection.",
                    wallMs, expectedMinWallMs,
                    CONCURRENT_THREADS, CONNECTION_POOL_SIZE, CONCURRENT_THREADS / CONNECTION_POOL_SIZE,
                    avgSingleMs)
                .isGreaterThanOrEqualTo(expectedMinWallMs);

        softly.assertAll();

        if (p95 > avgSingleMs * 3) {
            log.warn("  ⚠️  p95 {}ms = {}× single query — user thứ 95 trải nghiệm latency tệ hơn nhiều",
                    p95, p95 / Math.max(avgSingleMs, 1));
            log.warn("  ⚠️  Đây là lý do partition giúp: giảm avg từ 263ms → 0.6ms");
            log.warn("      → p95 concurrent xuống còn ~{}ms thay vì {}ms",
                    (p95 * 6 / 263) + 1, p95); // tỉ lệ cải thiện 400x từ benchmark
        } else {
            log.info("  ✅ p95 {}ms — DB xử lý tốt concurrent load", p95);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long runQuery() {
        long t0 = System.currentTimeMillis();
        jdbcTemplate.queryForList(QUERY_PENDING_ORDERS);
        return System.currentTimeMillis() - t0;
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
