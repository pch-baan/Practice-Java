CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(512) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_email_verification_tokens         PRIMARY KEY (id),
    CONSTRAINT uq_email_verification_token_hash     UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_tokens_user_id FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_user_id ON email_verification_tokens (user_id);
