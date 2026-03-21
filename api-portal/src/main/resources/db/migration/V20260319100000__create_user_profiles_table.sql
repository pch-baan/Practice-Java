CREATE TABLE IF NOT EXISTS user_profiles (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL,

    -- Display
    full_name     VARCHAR(100),
    display_name  VARCHAR(50),
    avatar_url    VARCHAR(500),
    bio           VARCHAR(500),

    -- Personal (GDPR-sensitive → nullable)
    phone_number  VARCHAR(20),
    date_of_birth DATE,
    gender        VARCHAR(20),

    -- Locale
    locale        VARCHAR(10)   NOT NULL DEFAULT 'vi-VN',
    timezone      VARCHAR(50)   NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',

    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_user_profiles         PRIMARY KEY (id),
    CONSTRAINT uq_user_profiles_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_profiles_user    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_gender               CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'))
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_user_id ON user_profiles (user_id);
