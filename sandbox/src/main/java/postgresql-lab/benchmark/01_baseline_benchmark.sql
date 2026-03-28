-- ============================================================
-- BENCHMARK 01: Baseline — partial index on status=PENDING
-- Run: docker exec -it practice-db psql -U practice_user -d practice_db \
--           -f scripts/benchmark/01_baseline_benchmark.sql
-- Record all outputs in docs/partitioning_results.md
-- Run each query 3 times, take the median.
-- ============================================================

-- Inspect table sizes
SELECT
    tablename,
    pg_size_pretty(pg_total_relation_size('public.'||tablename)) AS total_size,
    pg_size_pretty(pg_relation_size('public.'||tablename))       AS table_size
FROM pg_tables
WHERE tablename IN ('orders', 'order_items')
ORDER BY tablename;

-- Inspect existing indexes
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'orders'
ORDER BY indexname;

-- Warm up shared_buffers (discard result)
SELECT COUNT(*) FROM orders WHERE status = 'PENDING';

-- ============================================================
-- B1: Fetch PENDING orders (hot path — worker polling)
-- Expected: Bitmap/Index Scan on idx_orders_status_pending
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, user_id, total_amount, created_at
FROM orders
WHERE status = 'PENDING'
ORDER BY created_at DESC
LIMIT 100;

-- ============================================================
-- B2: UPDATE 1 row — PENDING → CONFIRMED
-- Expected: fast, in-place update, no partition movement
-- ============================================================
BEGIN;
EXPLAIN (ANALYZE, BUFFERS)
UPDATE orders
SET status = 'CONFIRMED', updated_at = NOW()
WHERE id = (SELECT id FROM orders WHERE status = 'PENDING' LIMIT 1);
ROLLBACK;

-- ============================================================
-- B3: Bulk UPDATE 1000 rows — PENDING → CONFIRMED
-- ============================================================
BEGIN;
EXPLAIN (ANALYZE, BUFFERS)
UPDATE orders
SET status = 'CONFIRMED', updated_at = NOW()
WHERE id IN (
    SELECT id FROM orders WHERE status = 'PENDING' LIMIT 1000
);
ROLLBACK;

-- ============================================================
-- B4: Date-range scan — last 30 days, any status
-- Expected: Seq Scan (no index on created_at)
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*), SUM(total_amount)
FROM orders
WHERE created_at >= NOW() - INTERVAL '30 days';

-- ============================================================
-- B5: Date-range + PENDING filter
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, user_id, total_amount, created_at
FROM orders
WHERE status = 'PENDING'
  AND created_at >= NOW() - INTERVAL '30 days'
ORDER BY created_at DESC
LIMIT 50;

-- ============================================================
-- B6: Monthly revenue aggregation (full scan)
-- Expected: Seq Scan, slow
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    DATE_TRUNC('month', created_at) AS month,
    status,
    COUNT(*)                        AS order_count,
    SUM(total_amount)               AS revenue
FROM orders
GROUP BY 1, 2
ORDER BY 1 DESC, 2;
