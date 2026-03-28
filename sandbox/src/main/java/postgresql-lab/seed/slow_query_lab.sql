-- ============================================================
-- SLOW QUERY LAB — 8 bài tập thực chiến
-- Dùng EXPLAIN ANALYZE để đo trước & sau khi fix

-- docker exec -it practice-db psql -U practice_user -d practice_db
-- ============================================================

-- Cú pháp đo performance:
-- EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) <query>;


-- ============================================================
-- BÀI 1: Full Table Scan — lọc theo status
-- Vấn đề: không có index trên cột status
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, user_id, total_amount, status
FROM orders
WHERE status = 'PENDING';

-- Fix:
-- CREATE INDEX idx_orders_status ON orders(status);


-- ============================================================
-- BÀI 2: Full Table Scan — lọc theo user_id
-- Vấn đề: không có index trên cột user_id
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, status, total_amount, created_at
FROM orders
WHERE user_id = (SELECT user_id FROM orders LIMIT 1);

-- Fix:
-- CREATE INDEX idx_orders_user_id ON orders(user_id);


-- ============================================================
-- BÀI 3: Date Range Scan — truy vấn theo khoảng thời gian
-- Vấn đề: không có index trên created_at
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*), SUM(total_amount)
FROM orders
WHERE created_at >= NOW() - INTERVAL '30 days';

-- Fix:
-- CREATE INDEX idx_orders_created_at ON orders(created_at);


-- ============================================================
-- BÀI 4: JOIN không có index — chậm nhất khi data lớn
-- Vấn đề: order_items.order_id không có index (FK không tự tạo index)
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.id, o.status, SUM(oi.subtotal) AS total
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
WHERE o.status = 'DELIVERED'
GROUP BY o.id, o.status
LIMIT 100;

-- Fix:
-- CREATE INDEX idx_order_items_order_id ON order_items(order_id);


-- ============================================================
-- BÀI 5: N+1 Simulation — lấy 10 orders rồi fetch items từng cái
-- Vấn đề: 10 query riêng lẻ thay vì 1 JOIN
-- ============================================================

-- Cách chậm (N+1):
-- App code gọi: SELECT * FROM orders LIMIT 10;
-- Rồi với mỗi order, gọi: SELECT * FROM order_items WHERE order_id = ?

-- Cách đúng (1 query):
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.id, o.status, oi.product_name, oi.quantity, oi.unit_price
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
WHERE o.id IN (SELECT id FROM orders ORDER BY created_at DESC LIMIT 10);


-- ============================================================
-- BÀI 6: Deep Pagination — OFFSET lớn cực kỳ chậm
-- Vấn đề: OFFSET 500000 buộc DB phải đọc và bỏ 500K rows
-- ============================================================

-- Cách chậm:
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, user_id, status, total_amount
FROM orders
ORDER BY created_at DESC
OFFSET 500000 LIMIT 20;

-- Fix (Keyset Pagination / Cursor-based):
-- EXPLAIN (ANALYZE, BUFFERS)
-- SELECT id, user_id, status, total_amount, created_at
-- FROM orders
-- WHERE created_at < '2025-06-15 10:00:00+07'   -- cursor từ trang trước
-- ORDER BY created_at DESC
-- LIMIT 20;


-- ============================================================
-- BÀI 7: SELECT * — lấy dư cột không cần
-- Vấn đề: kéo tất cả columns, không dùng index-only scan
-- ============================================================

-- Cách chậm:
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM orders
WHERE status = 'CANCELLED'
ORDER BY created_at DESC
LIMIT 50;

-- Fix:
-- SELECT id, user_id, total_amount, created_at
-- FROM orders
-- WHERE status = 'CANCELLED'
-- ORDER BY created_at DESC
-- LIMIT 50;
-- + Covering index: CREATE INDEX idx_orders_status_cover ON orders(status, created_at DESC) INCLUDE (id, user_id, total_amount);


-- ============================================================
-- BÀI 8: Aggregation lớn — GROUP BY trên toàn bảng
-- Vấn đề: phải đọc toàn bộ 3M rows để tính
-- ============================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    DATE_TRUNC('month', o.created_at) AS month,
    o.status,
    COUNT(*)                           AS order_count,
    SUM(o.total_amount)                AS revenue
FROM orders o
GROUP BY 1, 2
ORDER BY 1 DESC, 2;

-- Fix:
-- CREATE INDEX idx_orders_created_at_status ON orders(created_at, status);
-- Hoặc dùng Materialized View nếu report chạy thường xuyên.
