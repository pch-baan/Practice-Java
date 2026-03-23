-- UNIQUE constraint trên email và username đã tự tạo ngầm B-tree index.
-- idx_users_email và idx_users_username là duplicate → xóa để tránh double maintenance.
-- idx_users_status giữ nguyên (không có UNIQUE constraint trên cột này).
DROP INDEX IF EXISTS idx_users_email;
DROP INDEX IF EXISTS idx_users_username;
