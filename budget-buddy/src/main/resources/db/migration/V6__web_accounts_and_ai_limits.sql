ALTER TABLE users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN demo_account BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET demo_account = TRUE WHERE email = 'user1@test.com';

CREATE TABLE ai_usage (
    usage_key VARCHAR(100) PRIMARY KEY,
    usage_day DATE NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0,
    last_requested_at TIMESTAMP,
    CONSTRAINT chk_ai_usage_count CHECK (request_count >= 0)
);
INSERT INTO ai_usage (usage_key, usage_day, request_count) VALUES ('global', CURRENT_DATE, 0);

ALTER TABLE ai_reports ADD COLUMN report_month VARCHAR(7);
