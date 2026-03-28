-- ============================================================
-- FIX: Missing Index on orders.status
-- Problem: Seq Scan on ~5M rows for WHERE status = 'PENDING'
--          → 4M rows removed by filter, 355ms execution time
-- Solution: Partial index on status = 'PENDING'
--           (PENDING is the hot path — dashboard, worker polling)
--           CONFIRMED/SHIPPED/DELIVERED/CANCELLED are rarely re-queried.
-- ============================================================

CREATE INDEX idx_orders_status_pending
    ON orders (status)
    WHERE status = 'PENDING';

-- Expected result:
--   BEFORE: Seq Scan   → ~355ms, reads 67,883 blocks from disk
--   AFTER:  Index Scan →   ~5ms, reads ~1,000 blocks from disk
