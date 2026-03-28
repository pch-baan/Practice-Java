-- ============================================================
-- LAB ONLY: Partition by LIST(status) — shadow schema experiment
-- Does NOT touch live `orders` table.
-- Rollback: V20260328100003__drop_orders_partitioned_by_status_lab.sql
-- After applying: run scripts/benchmark/02_load_data_into_status_partitions.sql
-- ============================================================

CREATE SCHEMA IF NOT EXISTS orders_lab;

-- Parent table — same schema as live orders, partitioned by status
CREATE TABLE orders_lab.orders_by_status (
    id               BIGSERIAL,
    user_id          UUID             NOT NULL,
    status           VARCHAR(20)      NOT NULL
                         CHECK (status IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED')),
    total_amount     NUMERIC(12, 2)   NOT NULL,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    shipping_address TEXT,
    note             TEXT,
    PRIMARY KEY (id, status)   -- PK must include partition key
) PARTITION BY LIST (status);

-- One partition per status value
CREATE TABLE orders_lab.orders_pending
    PARTITION OF orders_lab.orders_by_status
    FOR VALUES IN ('PENDING');

CREATE TABLE orders_lab.orders_confirmed
    PARTITION OF orders_lab.orders_by_status
    FOR VALUES IN ('CONFIRMED');

CREATE TABLE orders_lab.orders_shipped
    PARTITION OF orders_lab.orders_by_status
    FOR VALUES IN ('SHIPPED');

CREATE TABLE orders_lab.orders_delivered
    PARTITION OF orders_lab.orders_by_status
    FOR VALUES IN ('DELIVERED');

CREATE TABLE orders_lab.orders_cancelled
    PARTITION OF orders_lab.orders_by_status
    FOR VALUES IN ('CANCELLED');

-- Index on created_at per partition (for ORDER BY queries)
CREATE INDEX idx_lab_pending_created_at    ON orders_lab.orders_pending   (created_at DESC);
CREATE INDEX idx_lab_confirmed_created_at  ON orders_lab.orders_confirmed (created_at DESC);
CREATE INDEX idx_lab_shipped_created_at    ON orders_lab.orders_shipped   (created_at DESC);
CREATE INDEX idx_lab_delivered_created_at  ON orders_lab.orders_delivered (created_at DESC);
CREATE INDEX idx_lab_cancelled_created_at  ON orders_lab.orders_cancelled (created_at DESC);

COMMENT ON SCHEMA orders_lab IS
    'LAB: Partition experiments. Safe to drop via V20260328100003.';
COMMENT ON TABLE orders_lab.orders_by_status IS
    'LAB: LIST partition by status. Mirrors live orders schema. Temporary.';
