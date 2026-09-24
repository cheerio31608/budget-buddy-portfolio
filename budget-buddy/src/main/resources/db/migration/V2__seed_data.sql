INSERT INTO users (user_id, email, password_hash, balance)
VALUES (1, 'user1@test.com', 'pw_hash_value_here', 0.00);

INSERT INTO categories (category_id, user_id, name, type)
VALUES
    (1, 1, 'Salary', 'INCOME'),
    (2, 1, 'Food', 'EXPENSE'),
    (3, 1, 'Transport', 'EXPENSE'),
    (4, 1, 'Housing', 'EXPENSE'),
    (5, 1, 'Medical', 'EXPENSE'),
    (6, 1, 'Culture', 'EXPENSE');
