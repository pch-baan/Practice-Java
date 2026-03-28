-- ============================================================
-- BENCHMARK 03: RANGE partition by created_at + status indexes
-- Run AFTER V20260328100004 migration is applied and verified.
-- Compare all numbers against 01 and 02 benchmark outputs.
-- ============================================================

-- Verify partition pruning is enabled
SHOW enable_partition_pruning;

-- Inspect partition layout and sizes
SELECT
    child.relname                                       AS partition,
    pg_size_pretty(pg_relation_size(child.oid))         AS size,
    s.n_live_tup                                        AS approx_rows
FROM pg_inherits
JOIN pg_class child ON pg_inherits.inhrelid = child.oid
JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
LEFT JOIN pg_stat_user_tables s ON s.relname = child.relname
WHERE parent.relname = 'orders'
ORDER BY child.relname;

-- Warm up
SELECT COUNT(*) FROM orders WHERE status = 'PENDING';

-- ============================================================
-- B1a: Fetch PENDING with date filter
-- Expected: prune to 1-2 recent partitions only
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, user_id, total_amount, created_at
FROM orders
WHERE status = 'PENDING'
  AND created_at >= NOW() - INTERVAL '30 days'
ORDER BY created_at DESC
LIMIT 100;

-- ============================================================
-- B1b: Fetch PENDING without date filter (all partitions)
-- Expected: all partitions scanned via partial index
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, user_id, total_amount, created_at
FROM orders
WHERE status = 'PENDING'
ORDER BY created_at DESC
LIMIT 100;

-- ============================================================
-- B2: UPDATE 1 row — PENDING → CONFIRMED
-- KEY METRIC: No partition boundary crossed (created_at unchanged)
-- Expected: fast in-place update, same as baseline
-- ============================================================
BEGIN;
EXPLAIN (ANALYZE, BUFFERS)
UPDATE orders
SET status = 'CONFIRMED', updated_at = NOW()
WHERE id = (SELECT id FROM orders WHERE status = 'PENDING' LIMIT 1);
ROLLBACK;

-- ============================================================
-- B3: Bulk UPDATE 1000 rows
-- Expected: comparable to baseline (no cross-partition movement)
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
-- B4: Date-range scan — partition pruning in effect
-- Expected: only 1-2 partitions scanned vs all 5M rows before
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*), SUM(total_amount)
FROM orders
WHERE created_at >= NOW() - INTERVAL '30 days';

-- ============================================================
-- B5: Date-range + PENDING — single partition, partial index
-- Expected: fastest query — 1 partition + index = minimal I/O
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, user_id, total_amount, created_at
FROM orders
WHERE status = 'PENDING'
  AND created_at >= NOW() - INTERVAL '30 days'
ORDER BY created_at DESC
LIMIT 50;

-- ============================================================
-- B6: Monthly aggregation
-- Expected: partition-aligned grouping, faster than full table scan
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
