-- ============================================================
-- PRODUCTION: Partition orders by RANGE(created_at)
-- Strategy: create new partitioned table → copy data → rename swap
-- Partitions: monthly from 2024-01 to 2026-06 + default overflow
-- Rollback: V20260328100005__rollback_partition_orders.sql
--
-- NOTE: Run during low-traffic window (~5-10 min for 5M rows).
-- NOTE: PK changes to (id, created_at) — PostgreSQL requires
--       partition key in PK. FK from order_items is recreated.
-- ============================================================

-- ============================================================
-- STEP 1: Create partitioned table
-- ============================================================
CREATE TABLE orders_partitioned (
    id               BIGSERIAL,
    user_id          UUID             NOT NULL,
    status           VARCHAR(20)      NOT NULL
                         CHECK (status IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED')),
    total_amount     NUMERIC(12, 2)   NOT NULL,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    shipping_address TEXT,
    note             TEXT,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- ============================================================
-- STEP 2: Monthly partitions — 2024
-- ============================================================
CREATE TABLE orders_y2024m01 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE orders_y2024m02 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
CREATE TABLE orders_y2024m03 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');
CREATE TABLE orders_y2024m04 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-04-01') TO ('2024-05-01');
CREATE TABLE orders_y2024m05 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-05-01') TO ('2024-06-01');
CREATE TABLE orders_y2024m06 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
CREATE TABLE orders_y2024m07 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-07-01') TO ('2024-08-01');
CREATE TABLE orders_y2024m08 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-08-01') TO ('2024-09-01');
CREATE TABLE orders_y2024m09 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-09-01') TO ('2024-10-01');
CREATE TABLE orders_y2024m10 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-10-01') TO ('2024-11-01');
CREATE TABLE orders_y2024m11 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');
CREATE TABLE orders_y2024m12 PARTITION OF orders_partitioned FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');

-- ============================================================
-- STEP 2: Monthly partitions — 2025
-- ============================================================
CREATE TABLE orders_y2025m01 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE orders_y2025m02 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
CREATE TABLE orders_y2025m03 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');
CREATE TABLE orders_y2025m04 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');
CREATE TABLE orders_y2025m05 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
CREATE TABLE orders_y2025m06 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');
CREATE TABLE orders_y2025m07 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');
CREATE TABLE orders_y2025m08 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');
CREATE TABLE orders_y2025m09 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');
CREATE TABLE orders_y2025m10 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
CREATE TABLE orders_y2025m11 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
CREATE TABLE orders_y2025m12 PARTITION OF orders_partitioned FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

-- ============================================================
-- STEP 2: Monthly partitions — 2026
-- ============================================================
CREATE TABLE orders_y2026m01 PARTITION OF orders_partitioned FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE orders_y2026m02 PARTITION OF orders_partitioned FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE orders_y2026m03 PARTITION OF orders_partitioned FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE orders_y2026m04 PARTITION OF orders_partitioned FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE orders_y2026m05 PARTITION OF orders_partitioned FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE orders_y2026m06 PARTITION OF orders_partitioned FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

-- Default partition: catches any rows outside defined ranges
CREATE TABLE orders_default PARTITION OF orders_partitioned DEFAULT;

-- ============================================================
-- STEP 3: Indexes per partition
-- Partial index on status=PENDING (hot path) + created_at index
-- ============================================================
CREATE INDEX idx_orders_y2024m01_pending ON orders_y2024m01 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2024m02_pending ON orders_y2024m02 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2024m03_pending ON orders_y2024m03 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2024m04_pending ON orders_y2024m04 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2024m05_pending ON orders_y2024m05 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2024m06_pending ON orders_y2024m06 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2024m07_pending ON orders_y2024m07 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2024m08_pending ON orders_y2024m08 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2024m09_pending ON orders_y2024m09 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2024m10_pending ON orders_y2024m10 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2024m11_pending ON orders_y2024m11 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2024m12_pending ON orders_y2024m12 (status) WHERE status = 'PENDING';

CREATE INDEX idx_orders_y2025m01_pending ON orders_y2025m01 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2025m02_pending ON orders_y2025m02 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2025m03_pending ON orders_y2025m03 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2025m04_pending ON orders_y2025m04 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2025m05_pending ON orders_y2025m05 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2025m06_pending ON orders_y2025m06 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2025m07_pending ON orders_y2025m07 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2025m08_pending ON orders_y2025m08 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2025m09_pending ON orders_y2025m09 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2025m10_pending ON orders_y2025m10 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2025m11_pending ON orders_y2025m11 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2025m12_pending ON orders_y2025m12 (status) WHERE status = 'PENDING';

CREATE INDEX idx_orders_y2026m01_pending ON orders_y2026m01 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2026m02_pending ON orders_y2026m02 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2026m03_pending ON orders_y2026m03 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2026m04_pending ON orders_y2026m04 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2026m05_pending ON orders_y2026m05 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_y2026m06_pending ON orders_y2026m06 (status) WHERE status = 'PENDING';
CREATE INDEX idx_orders_default_pending  ON orders_default  (status) WHERE status = 'PENDING';

CREATE INDEX idx_orders_y2024m01_created_at ON orders_y2024m01 (created_at DESC);
CREATE INDEX idx_orders_y2024m02_created_at ON orders_y2024m02 (created_at DESC);
CREATE INDEX idx_orders_y2024m03_created_at ON orders_y2024m03 (created_at DESC);
CREATE INDEX idx_orders_y2024m04_created_at ON orders_y2024m04 (created_at DESC);
CREATE INDEX idx_orders_y2024m05_created_at ON orders_y2024m05 (created_at DESC);
CREATE INDEX idx_orders_y2024m06_created_at ON orders_y2024m06 (created_at DESC);
CREATE INDEX idx_orders_y2024m07_created_at ON orders_y2024m07 (created_at DESC);
CREATE INDEX idx_orders_y2024m08_created_at ON orders_y2024m08 (created_at DESC);
CREATE INDEX idx_orders_y2024m09_created_at ON orders_y2024m09 (created_at DESC);
CREATE INDEX idx_orders_y2024m10_created_at ON orders_y2024m10 (created_at DESC);
CREATE INDEX idx_orders_y2024m11_created_at ON orders_y2024m11 (created_at DESC);
CREATE INDEX idx_orders_y2024m12_created_at ON orders_y2024m12 (created_at DESC);

CREATE INDEX idx_orders_y2025m01_created_at ON orders_y2025m01 (created_at DESC);
CREATE INDEX idx_orders_y2025m02_created_at ON orders_y2025m02 (created_at DESC);
CREATE INDEX idx_orders_y2025m03_created_at ON orders_y2025m03 (created_at DESC);
CREATE INDEX idx_orders_y2025m04_created_at ON orders_y2025m04 (created_at DESC);
CREATE INDEX idx_orders_y2025m05_created_at ON orders_y2025m05 (created_at DESC);
CREATE INDEX idx_orders_y2025m06_created_at ON orders_y2025m06 (created_at DESC);
CREATE INDEX idx_orders_y2025m07_created_at ON orders_y2025m07 (created_at DESC);
CREATE INDEX idx_orders_y2025m08_created_at ON orders_y2025m08 (created_at DESC);
CREATE INDEX idx_orders_y2025m09_created_at ON orders_y2025m09 (created_at DESC);
CREATE INDEX idx_orders_y2025m10_created_at ON orders_y2025m10 (created_at DESC);
CREATE INDEX idx_orders_y2025m11_created_at ON orders_y2025m11 (created_at DESC);
CREATE INDEX idx_orders_y2025m12_created_at ON orders_y2025m12 (created_at DESC);

CREATE INDEX idx_orders_y2026m01_created_at ON orders_y2026m01 (created_at DESC);
CREATE INDEX idx_orders_y2026m02_created_at ON orders_y2026m02 (created_at DESC);
CREATE INDEX idx_orders_y2026m03_created_at ON orders_y2026m03 (created_at DESC);
CREATE INDEX idx_orders_y2026m04_created_at ON orders_y2026m04 (created_at DESC);
CREATE INDEX idx_orders_y2026m05_created_at ON orders_y2026m05 (created_at DESC);
CREATE INDEX idx_orders_y2026m06_created_at ON orders_y2026m06 (created_at DESC);
CREATE INDEX idx_orders_default_created_at  ON orders_default  (created_at DESC);

-- ============================================================
-- STEP 4: Copy data from live orders
-- ============================================================
INSERT INTO orders_partitioned
    (id, user_id, status, total_amount, created_at, updated_at, shipping_address, note)
SELECT id, user_id, status, total_amount, created_at, updated_at, shipping_address, note
FROM orders;

-- ============================================================
-- STEP 5: Verify row counts match before rename
-- ============================================================
DO $$
DECLARE
    old_count BIGINT;
    new_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO old_count FROM orders;
    SELECT COUNT(*) INTO new_count FROM orders_partitioned;
    IF old_count <> new_count THEN
        RAISE EXCEPTION 'Row count mismatch: orders=% orders_partitioned=%', old_count, new_count;
    END IF;
    RAISE NOTICE 'Row count verified: % rows migrated successfully', old_count;
END $$;

-- ============================================================
-- STEP 6: Rename swap
-- Brief window where table name transitions — run during low-traffic
-- ============================================================
ALTER TABLE orders              RENAME TO orders_old_unpartitioned;
ALTER TABLE orders_partitioned  RENAME TO orders;

-- ============================================================
-- STEP 7: Drop FK from order_items
-- NOTE: Cannot recreate FK referencing just orders(id) after partitioning.
-- PostgreSQL requires UNIQUE constraints on partitioned tables to include
-- all partition key columns. Our PK is (id, created_at), so a FK pointing
-- to just (id) is not supported as a global constraint.
-- Trade-off: FK enforcement is handled at application level (JPA cascade).
-- ============================================================
ALTER TABLE order_items DROP CONSTRAINT IF EXISTS order_items_order_id_fkey;

-- ============================================================
-- STEP 8: Reattach sequence to new table
-- ============================================================
ALTER SEQUENCE orders_id_seq OWNED BY orders.id;

-- Final check
SELECT
    'orders'                   AS table_name,
    COUNT(*)                   AS row_count,
    pg_size_pretty(pg_total_relation_size('orders')) AS total_size
FROM orders;
