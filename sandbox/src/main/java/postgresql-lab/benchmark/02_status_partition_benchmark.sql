-- ============================================================
-- BENCHMARK 02: LIST partition by status (orders_lab schema)
-- Run AFTER 02_load_data_into_status_partitions.sql
-- Compare all numbers against 01_baseline_benchmark.sql output
-- ============================================================

-- Confirm data loaded
SELECT COUNT(*) AS total_rows FROM orders_lab.orders_by_status;

-- Inspect partition sizes
SELECT
    tableoid::regclass                                  AS partition,
    COUNT(*)                                            AS row_count,
    pg_size_pretty(pg_relation_size(tableoid::regclass)) AS size
FROM orders_lab.orders_by_status
GROUP BY tableoid
ORDER BY tableoid::regclass::text;

-- ============================================================
-- B1: Fetch PENDING — should scan ONLY orders_lab.orders_pending
-- Look for: "Seq Scan on orders_pending" (1 of 5 partitions)
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, user_id, total_amount, created_at
FROM orders_lab.orders_by_status
WHERE status = 'PENDING'
ORDER BY created_at DESC
LIMIT 100;

-- ============================================================
-- B2: UPDATE 1 row — PENDING → CONFIRMED
-- KEY METRIC: Measures cross-partition DELETE + INSERT overhead
-- Look for: Delete on orders_pending + Insert on orders_confirmed
-- ============================================================
BEGIN;
EXPLAIN (ANALYZE, BUFFERS)
UPDATE orders_lab.orders_by_status
SET status = 'CONFIRMED', updated_at = NOW()
WHERE id = (
    SELECT id FROM orders_lab.orders_by_status WHERE status = 'PENDING' LIMIT 1
);
ROLLBACK;

-- ============================================================
-- B3: Bulk UPDATE 1000 rows — PENDING → CONFIRMED
-- Compare execution time vs baseline B3
-- ============================================================
BEGIN;
EXPLAIN (ANALYZE, BUFFERS)
UPDATE orders_lab.orders_by_status
SET status = 'CONFIRMED', updated_at = NOW()
WHERE id IN (
    SELECT id FROM orders_lab.orders_by_status WHERE status = 'PENDING' LIMIT 1000
);
ROLLBACK;

-- ============================================================
-- B4: Date-range scan — no partition pruning (status not filtered)
-- Expected: ALL 5 partitions scanned → similar or worse than baseline
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*), SUM(total_amount)
FROM orders_lab.orders_by_status
WHERE created_at >= NOW() - INTERVAL '30 days';

-- ============================================================
-- B5: Date-range + PENDING — 1 partition pruned + created_at filter
-- Expected: best case for this partition strategy
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, user_id, total_amount, created_at
FROM orders_lab.orders_by_status
WHERE status = 'PENDING'
  AND created_at >= NOW() - INTERVAL '30 days'
ORDER BY created_at DESC
LIMIT 50;

-- ============================================================
-- B6: Monthly aggregation — all partitions, no pruning
-- Expected: comparable or worse than baseline
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    DATE_TRUNC('month', created_at) AS month,
    status,
    COUNT(*)                        AS order_count,
    SUM(total_amount)               AS revenue
FROM orders_lab.orders_by_status
GROUP BY 1, 2
ORDER BY 1 DESC, 2;
