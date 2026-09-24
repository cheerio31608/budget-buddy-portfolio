-- =============================================================================
-- 1. 테이블 생성 (DDL)
-- =============================================================================

-- 1.1. 회원 관리 (Users)
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE users IS '사용자 기본 정보 테이블';
COMMENT ON COLUMN users.user_id IS '사용자 고유 ID (PK)';
COMMENT ON COLUMN users.email IS '로그인 이메일 (계정명)';
COMMENT ON COLUMN users.password_hash IS '암호화된 비밀번호 해시값';
COMMENT ON COLUMN users.balance IS '현재 보유 잔액';
COMMENT ON COLUMN users.created_at IS '가입 일시';

-- 1.2. 카테고리 관리 (Categories)
CREATE TABLE categories (
    category_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL, -- INCOME / EXPENSE
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE categories IS '수입/지출 분류 카테고리';
COMMENT ON COLUMN categories.category_id IS '카테고리 ID (PK)';
COMMENT ON COLUMN categories.user_id IS '소유자 ID (FK)';
COMMENT ON COLUMN categories.name IS '카테고리명 (예: 식비, 월급)';
COMMENT ON COLUMN categories.type IS '거래 유형 (INCOME: 수입, EXPENSE: 지출)';
COMMENT ON COLUMN categories.created_at IS '생성 일시';

-- 1.3. 거래 내역 (Transactions)
CREATE TABLE transactions (
    transaction_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_before DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL, -- INCOME / EXPENSE
    description TEXT,
    vendor_name VARCHAR(255),
    location VARCHAR(255),
    transaction_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE transactions IS '사용자의 수입 및 지출 거래 내역 테이블';
COMMENT ON COLUMN transactions.transaction_id IS '거래 고유 ID (PK)';
COMMENT ON COLUMN transactions.user_id IS '사용자 ID (FK)';
COMMENT ON COLUMN transactions.category_id IS '카테고리 ID (FK)';
COMMENT ON COLUMN transactions.amount IS '거래 금액 (항상 양수)';
COMMENT ON COLUMN transactions.balance_before IS '거래 발생 직전 잔액';
COMMENT ON COLUMN transactions.balance_after IS '거래 발생 직후 잔액';
COMMENT ON COLUMN transactions.transaction_type IS '거래 유형 (INCOME / EXPENSE)';
COMMENT ON COLUMN transactions.description IS '거래 설명';
COMMENT ON COLUMN transactions.vendor_name IS '가맹점 또는 거래처 이름';
COMMENT ON COLUMN transactions.location IS '거래 발생 위치';
COMMENT ON COLUMN transactions.transaction_at IS '거래 발생 일시';
COMMENT ON COLUMN transactions.created_at IS '데이터 생성 일시';

-- 1.4. AI 분석 리포트 (AI Reports)
CREATE TABLE ai_reports (
    report_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    report_content TEXT NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_reports IS '금융 데이터 기반 AI 자동 생성 리포트';
COMMENT ON COLUMN ai_reports.report_id IS '리포트 고유 ID (PK)';
COMMENT ON COLUMN ai_reports.user_id IS '대상 사용자 ID (FK)';
COMMENT ON COLUMN ai_reports.report_type IS '분석 유형 (월간 요약, 소비 패턴 등)';
COMMENT ON COLUMN ai_reports.report_content IS 'AI 분석 결과 텍스트';
COMMENT ON COLUMN ai_reports.generated_at IS '생성 일시';
