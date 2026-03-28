-- ============================================================
-- MAINTENANCE: Add next month's partition
-- Run on the last day of each month (or automate via pg_cron).
-- Replace YYYY and MM with actual year and month values.
-- Example: adding 2026-07 before end of June 2026.
-- ============================================================

-- Step 1: Create partition
CREATE TABLE IF NOT EXISTS orders_y2026m07
    PARTITION OF orders
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

-- Step 2: Add partial index on PENDING (hot path)
CREATE INDEX IF NOT EXISTS idx_orders_y2026m07_pending
    ON orders_y2026m07 (status)
    WHERE status = 'PENDING';

-- Step 3: Add created_at index (for ORDER BY and date-range queries)
CREATE INDEX IF NOT EXISTS idx_orders_y2026m07_created_at
    ON orders_y2026m07 (created_at DESC);

-- Verify
SELECT
    relname                                     AS partition,
    pg_size_pretty(pg_relation_size(oid))       AS size
FROM pg_class
WHERE relname = 'orders_y2026m07';
