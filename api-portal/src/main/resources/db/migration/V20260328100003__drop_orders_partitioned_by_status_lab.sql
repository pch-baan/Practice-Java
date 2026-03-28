-- ============================================================
-- ROLLBACK: Drop LIST-partition-by-status lab schema
-- Safe to run at any time — only touches orders_lab schema.
-- Live `orders` table is NOT affected.
-- ============================================================

DROP SCHEMA IF EXISTS orders_lab CASCADE;
