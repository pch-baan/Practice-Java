-- ============================================================
-- PRACTICE: Slow Query Lab — Orders Schema
-- Purpose: 2 tables designed with intentional missing indexes
--          for slow query practice and optimization exercises.
-- ============================================================

-- ============================================================
-- TABLE: orders (~1M rows)
-- ============================================================
CREATE TABLE orders (
    id               BIGSERIAL        PRIMARY KEY,
    user_id          UUID             NOT NULL,
    status           VARCHAR(20)      NOT NULL
                         CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    total_amount     NUMERIC(12, 2)   NOT NULL,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    shipping_address TEXT,
    note             TEXT
);

-- [INTENTIONALLY NO INDEXES on user_id, status, created_at]
-- Slow query scenarios:
--   - Query by user_id   → full table scan
--   - Filter by status   → full table scan
--   - Date range query   → full table scan
--   - ORDER BY created_at → sort without index

-- ============================================================
-- TABLE: order_items (~3M rows, ~3 items per order)
-- ============================================================
CREATE TABLE order_items (
    id           BIGSERIAL       PRIMARY KEY,
    order_id     BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   BIGINT          NOT NULL,
    product_name VARCHAR(255)    NOT NULL,
    quantity     INT             NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(10, 2)  NOT NULL,
    subtotal     NUMERIC(12, 2)  NOT NULL,
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- [INTENTIONALLY NO INDEX on order_id]
-- PostgreSQL does NOT auto-create index for FK columns.
-- Slow query scenarios:
--   - JOIN orders → order_items  → nested loop without index
--   - Filter items by order_id   → full table scan on 3M rows
--   - Aggregate SUM(subtotal) per order → expensive without index
