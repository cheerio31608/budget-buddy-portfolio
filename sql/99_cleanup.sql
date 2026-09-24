-- =============================================================================
-- 99. 데이터 초기화 (Cleanup)
-- =============================================================================

-- 모든 데이터 삭제 (역순)
TRUNCATE TABLE ai_reports RESTART IDENTITY CASCADE;
TRUNCATE TABLE transactions RESTART IDENTITY CASCADE;
TRUNCATE TABLE categories RESTART IDENTITY CASCADE;
TRUNCATE TABLE users RESTART IDENTITY CASCADE;

-- 테이블 삭제가 필요한 경우 (주의해서 사용)
/*
DROP TABLE IF EXISTS ai_reports;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS users;
*/
