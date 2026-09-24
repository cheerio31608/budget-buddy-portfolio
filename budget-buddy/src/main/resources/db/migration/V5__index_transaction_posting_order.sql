CREATE INDEX idx_transactions_user_posting
    ON transactions (user_id, transaction_id DESC);
