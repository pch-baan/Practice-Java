-- ============================================================
-- DATA LOAD: Copy live orders → orders_lab.orders_by_status
-- Run ONCE after V20260328100002 migration is applied.
-- Expected time: ~3-5 minutes for 5M rows.
-- ============================================================

-- Check data exists in source
SELECT COUNT(*) AS source_rows FROM orders;

-- Load data
INSERT INTO orders_lab.orders_by_status
    (id, user_id, status, total_amount, created_at, updated_at, shipping_address, note)
SELECT id, user_id, status, total_amount, created_at, updated_at, shipping_address, note
FROM orders;

-- Verify partition distribution
SELECT
    tableoid::regclass AS partition,
    COUNT(*)           AS row_count
FROM orders_lab.orders_by_status
GROUP BY 1
ORDER BY 1;

-- Verify total matches source
SELECT
    (SELECT COUNT(*) FROM orders)                     AS source_count,
    (SELECT COUNT(*) FROM orders_lab.orders_by_status) AS lab_count,
    (SELECT COUNT(*) FROM orders) =
    (SELECT COUNT(*) FROM orders_lab.orders_by_status) AS counts_match;
