ALTER TABLE categories
    ADD CONSTRAINT uk_category_owner_type UNIQUE (category_id, user_id, type);

ALTER TABLE transactions
    DROP CONSTRAINT fk_transaction_category;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transaction_category_owner_type
        FOREIGN KEY (category_id, user_id, transaction_type)
        REFERENCES categories (category_id, user_id, type);

ALTER TABLE transactions
    ADD CONSTRAINT chk_transaction_balance_snapshot
        CHECK (
            (transaction_type = 'INCOME' AND balance_after = balance_before + amount)
            OR
            (transaction_type = 'EXPENSE' AND balance_after = balance_before - amount)
        );
