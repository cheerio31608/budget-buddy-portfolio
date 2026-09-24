# Budget Buddy

Budget Buddy는 거래와 잔액의 정합성을 지키면서 소비 데이터를 시각화하고, Google Gemini가 계산된 통계를 이해하기 쉬운 문장으로 해석하는 웹 가계부 서비스입니다.

Java 17과 Spring Boot 3 기반의 기존 백엔드 구조를 유지하고, 별도 프론트엔드 서버 없이 함께 실행되는 반응형 웹 화면을 추가했습니다. 한 번의 실행으로 로그인, 거래 관리, 대시보드, AI 리포트, CSV 임시 분석을 직접 체험할 수 있습니다.

> 실제 결제 게이트웨이·은행 연동·투자 조언 서비스가 아닙니다. 금융성 데이터에서 중요한 원자성, 동시성, 잔액 스냅샷, 입력 검증, 외부 AI 연동을 학습하고 설명하기 위한 개인 포트폴리오 프로젝트입니다.

> 처음 실행한다면 [저장소 루트 README의 H2 3분 실행 안내](../README.md#3분-만에-실행하기--postgresql-없이-h2)를 먼저 확인하세요.

## 핵심 기능

- **거래 관리**: 수입·지출 등록, 최근 등록순 조회, 검색, 기간·카테고리 필터
- **잔액 정합성**: 거래와 잔액을 한 트랜잭션에서 반영하고 변경 전·후 스냅샷 저장
- **소비 분석**: 기간 수입·지출, 이전 기간 대비, 평균 지출, 주요 카테고리·사용처 계산
- **데이터 시각화**: 월별 지출, 카테고리 비율, 주간 흐름, 수입·지출 비교
- **Gemini AI 소비 리포트**: 원본 거래 대신 서버에서 계산한 집계값을 해석
- **CSV 가계부 분석**: 미리보기, 자동·수동 컬럼 매핑, 행별 검증, 중복 의심 표시, 임시 분석
- **세션 로그인**: `users` 테이블 계정과 Spring Security를 이용한 로그인 및 사용자 데이터 격리
- **로컬 데모 데이터**: 빈 계정에 샘플 거래 30건을 추가해 차트와 분석을 즉시 체험

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Web, Bean Validation |
| Frontend | HTML5, CSS3, Vanilla JavaScript ES Modules, SVG/CSS Chart |
| Security | Spring Security, server-side HTTP Session, BCrypt, CSRF Token |
| Database | PostgreSQL, Spring Data JPA, Flyway |
| AI | Google Gemini API, Spring `RestClient` |
| Test | H2, JUnit 5, Mockito, AssertJ |
| Build / API Docs | Gradle, springdoc-openapi |

### 프론트엔드 기술 선택

현재 화면은 대시보드·거래·AI 리포트·CSV 분석의 네 영역이며 복잡한 전역 상태나 프론트엔드 라우팅이 필요하지 않습니다. 이 규모에서 React 프로젝트를 별도로 두면 Node 빌드, CORS, 배포 단위가 추가되어 백엔드 포트폴리오의 핵심이 흐려질 수 있다고 판단했습니다.

따라서 Spring Boot가 정적 HTML/CSS/JavaScript를 함께 제공하도록 구성했습니다. JavaScript는 기능별 함수로 나누고 API 통신을 한 곳에 모아, 프레임워크 없이도 요청 흐름을 따라가기 쉽게 만들었습니다. 차트는 외부 CDN에 의존하지 않는 SVG/CSS 렌더러를 사용합니다.

## 인증과 DB 연결 방식

Budget Buddy는 **A안: 애플리케이션 계정 로그인 + 서버에 미리 설정된 PostgreSQL 연결**을 사용합니다.

브라우저에서 PostgreSQL ID와 비밀번호를 받는 B안은 연결 권한이 큰 DB 자격 증명을 웹 입력과 세션에 노출하고, 사용자마다 데이터소스를 동적으로 관리해야 해 일반적인 웹 서비스 구조에 적합하지 않습니다. 대신 브라우저는 Budget Buddy 계정 이메일·비밀번호만 전송하고, 백엔드는 `users.password_hash`를 검증한 뒤 서버 세션으로 로그인 상태를 유지합니다.

- PostgreSQL 비밀번호와 Gemini API Key는 환경변수로만 주입
- DB 비밀번호를 브라우저, `localStorage`, 세션, 로그에 저장하지 않음
- 프론트엔드는 DB에 직접 연결하지 않고 `/api/**`만 호출
- 인증된 사용자 ID를 서버의 Principal에서 가져와 다른 사용자의 거래 접근 차단
- 상태 변경 요청에 CSRF 토큰 적용
- 데모 시드 계정만 Flyway에서 BCrypt 해시로 제공

운영 서비스로 확장한다면 회원가입·이메일 인증·비밀번호 재설정·세션 저장소·HTTPS·로그인 시도 제한을 추가해야 합니다.

## 프로젝트 구조

```text
budget-buddy
├─ sample-data/
│  └─ transactions-sample.csv       CSV 체험용 샘플
├─ src/main/java/com/finance/budget_buddy
│  ├─ config/                        Security, Clock 설정
│  ├─ controller/                    HTTP 요청·응답과 인증 사용자 전달
│  ├─ dto/
│  │  ├─ analysis/                   DB·CSV 공통 분석 입력/결과
│  │  ├─ auth/                       로그인·세션 응답
│  │  ├─ csv/                        미리보기·매핑·검증 응답
│  │  └─ gemini/                     Gemini 요청·응답
│  ├─ entity/                        User, Category, Transaction, AiReport
│  ├─ exception/                     공통 비즈니스 예외와 안전한 오류 응답
│  ├─ repository/                    JPA 조회와 비관적 락
│  ├─ security/                      DB 사용자 Principal, UserDetailsService
│  └─ service/
│     ├─ TransactionService          거래·잔액·스냅샷 도메인 로직
│     ├─ AnalysisService             DB와 무관한 공통 통계 계산
│     ├─ AnalysisQueryService        DB Entity를 분석 모델로 변환
│     ├─ AiReportService             집계 프롬프트와 리포트 저장
│     ├─ GeminiClient                Gemini HTTP 통신
│     └─ csv/                        파싱 → 매핑 → 검증 → 분석 조합
├─ src/main/resources
│  ├─ db/migration/                  Flyway 스키마·시드·데모 로그인
│  └─ static/                        반응형 웹 UI
└─ src/test                          서비스·Repository·동시성 테스트
```

## 데이터베이스 구조

| 테이블 | 역할 | 주요 필드 |
|---|---|---|
| `users` | 계정과 현재 잔액 | `email`, `password_hash`, `balance` |
| `categories` | 사용자별 수입·지출 분류 | `user_id`, `name`, `type` |
| `transactions` | 거래와 잔액 변경 이력 | `amount`, `balance_before`, `balance_after`, `vendor_name`, `transaction_at` |
| `ai_reports` | 생성된 AI 리포트 보관 | `user_id`, `report_type`, `report_content` |

Flyway의 DB 제약 조건도 금액 양수, 잔액 음수 금지, 거래 유형, 외래 키, 사용자별 멱등성 키를 검증합니다. 거래 조회에는 사용자·발생 시각 및 사용자·등록 순서 복합 인덱스를, AI 리포트 조회에는 사용자·생성 시각 복합 인덱스를 둡니다.

## 실행 방법

Docker Compose로 앱과 PostgreSQL을 함께 실행하는 절차와 환경변수 목록은 [루트 README](../README.md#docker-compose로-앱과-postgresql-실행)를 참고하세요. 아래는 직접 설치한 PostgreSQL 또는 H2 실행 방법입니다.

### 1. PostgreSQL 실행

기본 데이터베이스 이름은 `budget_buddy_portfolio`입니다. 빈 데이터베이스를 만들면 테이블과 데모 데이터는 Flyway가 생성합니다.

### 2. 환경변수 설정

Windows PowerShell 예시:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/budget_buddy_portfolio"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your-local-database-password"
$env:GEMINI_API_KEY="your-gemini-api-key"
```

실제 비밀번호와 API Key를 `application.properties`, README, Git 커밋에 저장하지 않습니다. `GEMINI_API_KEY`가 없으면 거래·CSV 분석은 정상 동작하고 AI 리포트 생성만 사용자용 오류 메시지를 반환합니다.

`DB_USERNAME`과 `DB_PASSWORD`는 Spring Boot 서버가 PostgreSQL에 연결할 때 쓰는 값입니다. 웹 로그인에는 PostgreSQL 계정이 아니라 아래 Budget Buddy 데모 계정을 입력합니다.

### 3. Spring Boot와 Frontend 실행

```powershell
cd budget-buddy
.\gradlew.bat bootRun
```

Frontend는 Spring Boot 정적 리소스로 포함되어 있어 별도 `npm` 실행이 필요하지 않습니다.

Gradle 진행률이 `80% EXECUTING`과 `:bootRun`에서 유지되는 것은 서버가 웹 요청을 기다리는 정상 상태입니다. 브라우저에서 접속한 뒤, 종료할 때 `Ctrl + C`를 누릅니다.

### 4. 접속

```text
http://localhost:8080
```

데모 계정:

```text
email: user1@test.com
password: password
```

데모 계정은 로컬 포트폴리오 실행 전용입니다. 운영 환경에서는 공개 시드 계정을 제거해야 합니다.

### PostgreSQL 없이 H2로 체험

```powershell
./gradlew.bat bootRun --args='--spring.profiles.active=h2 --server.port=18080'
```

접속 주소는 `http://localhost:18080`입니다. 애플리케이션을 종료하면 H2 데이터는 초기화됩니다.

## 사용 방법

1. 데모 계정으로 로그인합니다.
2. 빈 대시보드의 `샘플 거래 30건 넣기` 버튼을 누르거나 직접 첫 수입 거래를 추가합니다.
3. 데모 데이터도 기존 `TransactionService`를 통과하므로 잔액과 스냅샷이 함께 생성됩니다.
4. 지출 거래를 추가하면 잔액, 스냅샷, 대시보드가 함께 갱신됩니다.
5. AI 리포트에서 이번 달 집계 결과의 Gemini 해석을 생성합니다.
6. CSV 분석에서 [`sample-data/transactions-sample.csv`](sample-data/transactions-sample.csv)를 선택합니다.
7. 자동 매핑 결과를 확인하고 검증·임시 분석을 실행합니다.

## 데이터 분석 흐름

```text
DB Transaction ─┐
                ├─> AnalysisTransaction ─> AnalysisService ─> AnalysisResult ─> Dashboard
CSV 파일 ─> 파싱 ─> 매핑·행 검증 ───────────┘                       └─> Gemini ─> ai_reports
```

설계 의도는 세 가지입니다.

1. **계산은 서버가 담당합니다.** 금액은 `BigDecimal`로 합산하고 기간·월·주·카테고리 통계를 결정적으로 계산합니다.
2. **AI는 해석만 담당합니다.** Gemini에는 집계 결과를 전달하고, 숫자 재계산이나 투자 조언을 요구하지 않습니다.
3. **CSV와 DB가 분석 로직을 재사용합니다.** 서로 다른 입력을 `AnalysisTransaction`으로 변환한 뒤 동일한 `AnalysisService`를 호출합니다.

### CSV 처리 과정

1. `CsvParserService`가 5MB 이하 파일을 UTF-8로 읽고, 실패하면 MS949로 재시도합니다.
2. 쉼표·탭·세미콜론 구분자를 감지하고 따옴표 안의 쉼표와 이스케이프 따옴표를 처리합니다.
3. `CsvMappingService`가 일반적인 한글·영문 컬럼명을 자동 추천합니다.
4. 날짜, 금액, 필수 값, 수입·지출 값, 미래 거래를 행 단위로 검증합니다.
5. 동일한 날짜·사용처·카테고리·금액·유형 조합을 중복 의심으로 표시합니다.
6. 정상 행만 공통 분석 모델로 변환해 임시 분석합니다. 업로드 내용은 `transactions`에 저장하지 않습니다.

현재 CSV 기능은 안전한 체험을 위해 **임시 분석 모드**를 우선 구현했습니다. 향후 가져오기 모드는 사용자 카테고리 매칭, 중복 정책, 시간순 잔액 스냅샷, 전체 롤백 정책을 명시한 뒤 `TransactionService`를 재사용해 추가할 수 있습니다.

## 거래와 잔액 정합성

`TransactionService.createTransaction()`은 다음 작업을 하나의 `@Transactional` 범위에서 실행합니다.

1. `UserRepository.findByIdForUpdate()`로 사용자 row 비관적 락 획득
2. 카테고리 소유권과 거래 유형 검증
3. 미래 거래와 잔액 부족 검증
4. `balanceBefore` 기록
5. `User.balance` 변경
6. 실제 변경 결과를 `balanceAfter`에 기록하고 거래 저장

선택적인 `idempotencyKey`는 네트워크 재시도에 따른 중복 거래와 중복 차감을 막습니다. 같은 키에 같은 payload가 오면 기존 결과를 반환하고, 다른 payload가 오면 `409 Conflict`로 거절합니다.

`transactionAt`은 실제 발생 시각이며, `balanceBefore` / `balanceAfter`는 장부에 반영한 등록 순서 기준입니다. 과거 날짜 거래를 나중에 입력해도 기존 이력을 다시 쓰지 않고, 목록은 스냅샷 흐름을 따라 최근 등록순으로 보여줍니다.

## Gemini 리포트 생성 과정

1. `AnalysisQueryService`가 로그인 사용자의 거래와 카테고리를 조회합니다.
2. `AnalysisService`가 이번 기간 및 이전 기간 통계를 계산합니다.
3. `AiReportService`가 총수입·총지출·변화율·카테고리·사용처·평균·최대 지출로 프롬프트를 만듭니다.
4. DB 트랜잭션 밖에서 `GeminiClient`가 API Key를 `x-goog-api-key` 헤더로 전달합니다. URL이나 로그에 Key를 붙이지 않습니다.
5. 외부 응답을 받은 뒤 Repository의 짧은 트랜잭션으로 `ai_reports`에 `MONTHLY` 또는 `CSV_ANALYSIS` 유형을 저장합니다.
6. 외부 API 오류는 stack trace 대신 `A002`, `A003` 구조화 오류로 프론트엔드에 전달합니다.

## 주요 API

로그인 후 브라우저 세션 쿠키와 CSRF 헤더를 사용합니다. Swagger UI는 `http://localhost:8080/swagger-ui/index.html`에서 확인할 수 있습니다.

PowerShell에서 세션 인증 API를 직접 확인할 때는 같은 `WebRequestSession`으로 쿠키를 유지하고, `/api/auth/csrf`가 반환한 헤더 이름과 토큰을 상태 변경 요청에 전달합니다.

```powershell
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/csrf" -WebSession $session
$csrfHeader = @{}
$csrfHeader[$csrf.headerName] = $csrf.token
$loginBody = @{ email = "user1@test.com"; password = "password" } | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
  -Method Post -WebSession $session -Headers $csrfHeader `
  -ContentType "application/json" -Body $loginBody
Invoke-RestMethod -Uri "http://localhost:8080/api/transactions" -WebSession $session
```

로그인 같은 상태 변경 요청을 보낼 때 CSRF 토큰이 필요하고, 이후 조회 요청은 같은 세션 쿠키만 유지하면 됩니다. Swagger UI는 API 계약 확인용으로 두고, 전체 사용자 흐름은 웹 화면이나 위 예시로 확인하는 편이 간단합니다.

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/auth/csrf` | 로그인 전 CSRF 토큰 |
| POST | `/api/auth/login` | 계정 로그인 |
| GET | `/api/auth/me` | 현재 로그인 사용자 |
| POST | `/api/auth/logout` | 로그아웃 |
| GET | `/api/categories` | 로그인 사용자 카테고리 |
| GET | `/api/transactions` | 거래 목록(최근 등록순) |
| GET | `/api/transactions/{id}` | 본인 거래 단건 조회 |
| POST | `/api/transactions` | 거래 생성 |
| GET | `/api/analysis/dashboard` | 이번 달 대시보드 통계 |
| GET | `/api/analysis?start=...&end=...` | 지정 기간 통계 |
| POST | `/api/ai/report/monthly` | 월간 AI 리포트 생성·저장 |
| GET | `/api/ai/reports` | 저장된 AI 리포트 조회 |
| POST | `/api/csv/preview` | CSV 헤더·10행 미리보기와 자동 매핑 |
| POST | `/api/csv/analyze` | CSV 검증과 임시 분석 |
| POST | `/api/csv/ai-report` | CSV 집계 기반 AI 리포트 생성·저장 |
| POST | `/api/demo/seed?count=30` | 빈 계정에 재현 가능한 데모 거래 생성(local/h2/compose 전용) |

거래 생성·조회와 AI 리포트 API는 클라이언트에서 `userId`를 받지 않습니다. 대상 사용자는 항상 로그인 세션의 Principal에서 결정하며, 리포트 생성처럼 상태를 바꾸는 작업은 `POST`로만 제공합니다.

## 에러 처리

`BusinessException` → `ErrorCode` → `GlobalExceptionHandler` → `ErrorResponse`의 기존 흐름을 유지합니다.

```json
{
  "code": "CSV003",
  "message": "amount 필드에 유효한 CSV 컬럼을 연결해 주세요.",
  "status": 400
}
```

CSV 행 오류는 요청 전체를 실패시키지 않고 `rowNumber`, `field`, `message` 목록으로 반환합니다. 프론트엔드는 코드별로 잔액 부족, 로그인 실패, Gemini 미설정 등을 사용자가 이해할 수 있는 문장으로 표시합니다.

## 테스트

```powershell
./gradlew.bat test
```

위 `test` 작업은 `test` 프로파일과 H2를 사용하므로 로컬 PostgreSQL에 의존하지 않습니다. Docker 실행 후 `./gradlew.bat postgresTest`를 실행하면 같은 핵심 저장·롤백·동시성 검증을 Testcontainers PostgreSQL에서 수행합니다. 전체 빌드/검증은 `./gradlew.bat build postgresTest`, 웹 재시도 검증은 `node --test frontend-tests/transaction-request.test.mjs`입니다. Docker가 없으면 postgresTest는 실패하며 자동 건너뛰지 않습니다. 현재 PC에서는 Docker 부재로 PostgreSQL/Compose 실동작 검증이 남아 있습니다.

- 거래 생성, 수입·지출 잔액 변경, 스냅샷
- 잔액 부족, 미래 거래, 카테고리 소유권
- 금액·잔액 정밀도, 멱등키 payload 충돌, 과거 거래의 등록 순서 스냅샷
- 동일 사용자 동시 지출의 비관적 락 정합성
- Repository 소유권·멱등성·등록순·기간 범위 쿼리
- 전월 동기간 비교, 월별·카테고리 집계, 분석 종료일 상한
- DB 스냅샷 산식과 카테고리 소유자·유형 복합 제약
- JSON 401/403, Basic challenge 부재, 세션 ID 교체, 응답 DTO 계약
- 데모 데이터 1회 생성과 고정 Clock 재현성
- CSV 따옴표/쉼표, UTF-8·MS949 파싱
- CSV 자동 컬럼 매핑, 잘못된 날짜·금액·유형·빈 행 검증
- CSV 중복 의심 탐지
- AI 프롬프트가 집계 결과를 사용하고 리포트를 저장하는지 검증

## 주요 화면

> 아래 위치에 실행 화면을 캡처해 추가할 수 있습니다.

### 로그인

<!-- docs/images/login.png -->

### 대시보드

<!-- docs/images/dashboard.png -->

### 거래 내역

<!-- docs/images/transactions.png -->

### AI 소비 리포트

<!-- docs/images/ai-report.png -->

### CSV 미리보기·매핑·검증·분석

<!-- docs/images/csv-analysis.png -->

## 향후 개선 방향

- CSV 거래 가져오기: 카테고리 매칭 화면, 중복 제외 선택, 전체 롤백 정책
- 대량 데이터용 DB 집계 Projection과 페이지네이션
- AI 리포트 비동기 상태(`PENDING`, `COMPLETED`, `FAILED`)와 월별 캐시
- 추가된 Testcontainers PostgreSQL 테스트와 Compose 구성을 Docker 환경에서 실행 검증
- 회원가입, 이메일 인증, 비밀번호 재설정, 로그인 시도 제한
- 월별 예산과 카테고리 예산 초과 알림

## 관련 문서

- [저장소 포트폴리오 README](../README.md)
- [아키텍처와 데이터 흐름](docs/ARCHITECTURE.md)
- [면접 설명 가이드](docs/INTERVIEW_GUIDE.md)
- [Docker·CI·테스트와 변경 이유 학습 가이드](docs/BACKEND_IMPROVEMENTS.md)
- [초기 포트폴리오 설계 기록(legacy)](Budget-Buddy-Portfolio-Notion.md)
- [Budget Buddy - 백엔드 포트폴리오 (Notion)](https://app.notion.com/p/3ca6f800cbc28103bb65c9181ee0aaab?pvs=204)
