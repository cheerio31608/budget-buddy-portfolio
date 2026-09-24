-- =============================================================================
-- 5. 조회 및 분석 쿼리 (Queries & Analysis)
-- =============================================================================

-- 5.1. 특정 사용자의 최근 거래 내역 조회
SELECT *
FROM transactions
WHERE user_id = 1
ORDER BY transaction_at DESC
LIMIT 20;

-- 5.2. 월별 수입/지출 합계 분석
SELECT
    TO_CHAR(transaction_at, 'YYYY-MM') AS month,
    transaction_type,
    SUM(amount) AS total_amount
FROM transactions
WHERE user_id = 1
GROUP BY TO_CHAR(transaction_at, 'YYYY-MM'), transaction_type
ORDER BY month DESC;

-- 5.3. 카테고리별 지출 비중 조회
SELECT
    c.name AS category_name,
    SUM(t.amount) AS total_amount,
    ROUND(SUM(t.amount) / SUM(SUM(t.amount)) OVER() * 100, 2) AS percentage
FROM transactions t
JOIN categories c ON t.category_id = c.category_id
WHERE t.user_id = 1 AND t.transaction_type = 'EXPENSE'
GROUP BY c.name
ORDER BY total_amount DESC;
