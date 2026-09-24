-- =============================================================================
-- 3. 초기 마스터 데이터 (Seed Data)
-- =============================================================================

-- 3.1. 기본 사용자
INSERT INTO users (user_id, email, password_hash)
VALUES (1, 'user1@test.com', 'pw_hash_value_here');

-- 3.2. 기본 카테고리
INSERT INTO categories (category_id, user_id, name, type)
VALUES
(1, 1, '수입', 'INCOME'),
(2, 1, '식비', 'EXPENSE'),
(3, 1, '교통비', 'EXPENSE'),
(4, 1, '주거/통신', 'EXPENSE'),
(5, 1, '의료/건강', 'EXPENSE'),
(6, 1, '문화/여가', 'EXPENSE');
