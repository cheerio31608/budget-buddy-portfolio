-- =============================================================================
-- 2. 제약 조건 및 인덱스 (Constraints & Indices)
-- =============================================================================

-- 2.1. Categories 외래 키
ALTER TABLE categories
ADD CONSTRAINT fk_category_user
    FOREIGN KEY (user_id) REFERENCES users(user_id);

-- 2.2. Transactions 외래 키 및 제약 조건
ALTER TABLE transactions
ADD CONSTRAINT fk_transaction_user
    FOREIGN KEY (user_id) REFERENCES users(user_id),
ADD CONSTRAINT fk_transaction_category
    FOREIGN KEY (category_id) REFERENCES categories(category_id);

ALTER TABLE transactions
ADD CONSTRAINT chk_amount_positive
    CHECK (amount > 0),
ADD CONSTRAINT chk_balance_before_non_negative
    CHECK (balance_before >= 0);

-- 2.3. AI Reports 외래 키
ALTER TABLE ai_reports
ADD CONSTRAINT fk_ai_report_user
    FOREIGN KEY (user_id) REFERENCES users(user_id);

-- 2.4. 성능 최적화를 위한 인덱스
CREATE INDEX idx_transactions_user_time
ON transactions (user_id, transaction_at DESC);
