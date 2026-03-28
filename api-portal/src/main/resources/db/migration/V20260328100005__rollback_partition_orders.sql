-- ============================================================
-- ROLLBACK: Restore orders from orders_old_unpartitioned
-- Run ONLY if V20260328100004 was applied and needs to be reversed.
-- Requires orders_old_unpartitioned to still exist.
-- ============================================================

-- Safety check: abort if old table does not exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_tables
        WHERE schemaname = 'public' AND tablename = 'orders_old_unpartitioned'
    ) THEN
        RAISE EXCEPTION 'orders_old_unpartitioned does not exist — rollback not possible';
    END IF;
END $$;

-- Step 1: Drop FK pointing to new partitioned orders
ALTER TABLE order_items DROP CONSTRAINT order_items_order_id_fkey;

-- Step 2: Rename partitioned table away
ALTER TABLE orders RENAME TO orders_partitioned_dropped;

-- Step 3: Restore original table
ALTER TABLE orders_old_unpartitioned RENAME TO orders;

-- Step 4: Restore FK
ALTER TABLE order_items
    ADD CONSTRAINT order_items_order_id_fkey
    FOREIGN KEY (order_id)
    REFERENCES orders (id)
    ON DELETE CASCADE;

-- Step 5: Restore sequence ownership
ALTER SEQUENCE orders_id_seq OWNED BY orders.id;

-- Step 6: Drop the failed partitioned table and all its child partitions
DROP TABLE IF EXISTS orders_partitioned_dropped CASCADE;

-- Verify
SELECT COUNT(*) AS restored_row_count FROM orders;
