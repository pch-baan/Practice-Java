-- Thêm PENDING vào CHECK constraint của cột status
-- PENDING: user đã đăng ký nhưng chưa xác thực email

ALTER TABLE users DROP CONSTRAINT chk_users_status;

ALTER TABLE users ADD CONSTRAINT chk_users_status
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING'));
