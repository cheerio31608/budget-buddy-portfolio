# Budget Buddy Architecture

이 문서는 코드를 처음 보는 사람이 요청 흐름과 각 계층의 책임을 빠르게 이해하도록 돕습니다. 구현보다 큰 패턴을 억지로 도입하지 않고, Spring Boot의 Controller → Service → Repository 구조 안에서 금융 데이터 정합성을 지키는 데 초점을 맞췄습니다.

## 1. 전체 구조

```text
Browser (HTML / CSS / JavaScript)
  │  Session Cookie + CSRF Header
  ▼
Controller                     HTTP 계약, 인증 사용자 전달
  ▼
Service                        도메인 검증, 계산, 트랜잭션 경계
  ▼
Repository                     JPA 조회, 잠금, 영속화
  ▼
PostgreSQL / H2                제약 조건과 인덱스로 최종 무결성 보강
```

| 패키지 | 책임 | 대표 클래스 |
|---|---|---|
| `controller` | 요청 검증, Principal 확인, 응답 반환 | `TransactionController`, `CsvAnalysisController` |
| `service` | 거래·분석·AI 비즈니스 흐름 | `TransactionService`, `AnalysisService`, `AiReportService` |
| `service.csv` | CSV 파싱, 매핑, 행별 검증 | `CsvParserService`, `CsvMappingService` |
| `repository` | 사용자별 조회와 비관적 락 | `UserRepository`, `TransactionRepository` |
| `entity` | 영속 도메인 상태 | `User`, `Transaction`, `Category`, `AiReport` |
| `dto` | API 입력·출력과 공통 분석 모델 | `TransactionCreateRequest`, `AnalysisResult` |
| `exception` | 예측 가능한 오류 코드와 안전한 응답 | `BusinessException`, `GlobalExceptionHandler` |
| `security` | DB 사용자 인증과 세션 Principal | `BudgetBuddyPrincipal`, `BudgetBuddyUserDetailsService` |

## 2. 거래 생성과 잔액 정합성

거래 생성은 단순 INSERT가 아닙니다. `TransactionService.createTransaction()`이 다음 작업을 하나의 `@Transactional` 범위에서 처리합니다.

```text
POST /api/transactions
  → 세션 Principal에서 userId 결정
  → User row PESSIMISTIC_WRITE 잠금
  → 선택적 idempotencyKey로 기존 처리 결과와 payload 확인
  → 카테고리 존재·소유권·거래 유형 확인
  → 미래 거래와 잔액 부족 검증
  → balanceBefore 기록
  → User.balance 변경
  → balanceAfter 기록
  → Transaction 저장
```

사용자 row를 먼저 잠그는 이유는 같은 사용자의 동시 요청이 같은 초기 잔액을 읽는 Race Condition을 막기 위해서입니다. 서로 다른 사용자의 거래는 서로 다른 row를 잠그므로 불필요하게 전체 테이블을 직렬화하지 않습니다.

서비스 검증에 더해 Flyway 스키마가 금액 양수, 잔액 음수 금지, 거래 유형, 외래 키, 사용자별 멱등성 키를 제약 조건으로 다시 확인합니다.

`transactionAt`은 거래가 실제 발생한 시각이고, `transactionId`/`createdAt`은 장부에 등록한 순서를 나타냅니다. `balanceBefore`와 `balanceAfter`는 **장부 등록 순서 기준 스냅샷**입니다. 따라서 과거 날짜 거래를 나중에 입력해도 이미 기록된 과거 스냅샷을 재작성하지 않으며, 거래 목록은 최근 등록순으로 제공합니다. CSV를 DB로 가져오는 기능은 이 발생 시각과 등록 순서 정책을 사용자가 선택할 수 있게 설계한 뒤 추가할 예정입니다.

## 3. 공통 분석 구조

DB Entity와 CSV 행은 형태와 생명주기가 다르지만 소비 통계 규칙은 같습니다. 두 입력을 작은 공통 모델인 `AnalysisTransaction`으로 변환한 뒤 동일한 `AnalysisService`를 호출합니다.

```text
DB Transaction ──> AnalysisQueryService ─┐
                                        ├─> AnalysisTransaction[]
CSV File ─> Parse ─> Map / Validate ─────┘             │
                                                       ▼
                                               AnalysisService
                                                       │
                                                       ▼
                                                AnalysisResult
                                                  ├─> Dashboard
                                                  └─> Gemini Prompt
```

`AnalysisService`는 JPA나 HTTP에 의존하지 않는 계산 전용 서비스입니다. `BigDecimal`로 총수입·총지출·평균·변화율을 계산하고, 월별·주별·카테고리별 결과 DTO를 만듭니다. 이 분리 덕분에 CSV와 DB 계산이 달라지는 문제를 줄이고 단위 테스트를 빠르게 작성할 수 있습니다.

## 4. CSV 임시 분석

```text
1. CsvParserService
   - 5MB 제한
   - UTF-8 우선, MS949 재시도
   - 쉼표·탭·세미콜론 감지
   - 따옴표와 이스케이프 처리

2. CsvMappingService
   - 한글·영문 헤더 자동 추천
   - 사용자 매핑 반영
   - 날짜·금액·필수 값·유형·미래 거래 검증
   - 중복 의심 표시

3. CsvAnalysisService
   - 정상 행만 AnalysisTransaction으로 변환
   - AnalysisService 재사용
   - 원본 행을 transactions에 저장하지 않음
```

오류 행 하나 때문에 파일 전체를 실패시키지 않습니다. `rowNumber`, `field`, `message`를 반환해 사용자가 원본 파일을 수정할 수 있게 했습니다. 현재는 임시 분석만 지원하며, 가져오기는 잔액 스냅샷과 전체 롤백 정책을 먼저 결정한 뒤 추가합니다.

## 5. Gemini 리포트

Gemini는 계산 엔진이 아니라 해석 계층입니다.

```text
AnalysisResult
  → 총수입·총지출·이전 기간·변화율·카테고리·사용처 요약
  → AiReportService가 제한된 프롬프트 구성
  → GeminiClient가 x-goog-api-key 헤더로 요청
  → 결과를 ai_reports에 저장
  → Frontend에서 이전 리포트 재조회
```

API Key는 환경변수로만 주입하고 URL이나 로그에 포함하지 않습니다. 외부 API 실패는 `A002`, `A003` 같은 내부 오류 코드로 변환하며 외부 응답이나 stack trace를 브라우저에 노출하지 않습니다.

## 6. 인증과 사용자 격리

브라우저는 PostgreSQL 계정이 아니라 `users` 테이블의 Budget Buddy 계정으로 로그인합니다. 로그인 성공 후 Spring SecurityContext를 HTTP Session에 저장하고, 상태 변경 요청은 CSRF 토큰을 함께 전송합니다.

Controller는 클라이언트가 임의로 전달한 사용자 식별자를 신뢰하지 않고 세션 Principal의 `userId`를 Service에 전달합니다. Repository 조회도 사용자 ID를 함께 조건으로 사용해 다른 사용자의 거래와 리포트를 조회하지 못하게 합니다.

## 7. 에러 처리

```text
Business rule violation
  → BusinessException(ErrorCode)
  → GlobalExceptionHandler
  → { code, message, status }
  → Frontend friendly message
```

예상 가능한 도메인 실패와 예상하지 못한 서버 오류를 구분합니다. CSV 행 오류는 분석 응답 안의 목록으로 제공하고, 인증·잔액·AI 연결 오류는 공통 `ErrorResponse`로 제공합니다.

## 8. 데이터베이스 변경 기준

애플리케이션이 사용하는 스키마의 유일한 기준은 `src/main/resources/db/migration/`입니다.

- `V1__init_schema.sql`: 테이블, 제약 조건, 인덱스
- `V2__seed_data.sql`: 로컬 데모 사용자와 카테고리
- `V3__secure_demo_user.sql`: 데모 비밀번호 BCrypt 전환
- `V4__strengthen_transaction_invariants.sql`: 스냅샷 산식과 카테고리 소유자·유형을 DB에서도 검증
- `V5__index_transaction_posting_order.sql`: 사용자별 최근 등록순 거래 조회를 위한 복합 인덱스

저장소 루트의 `sql/`은 초기 설계 과정을 남긴 참고 자료이며 런타임에는 실행하지 않습니다. 실제 변경은 새 Flyway 버전 파일을 추가하는 방식으로만 진행합니다.

## 9. 테스트 경계

- Service 단위 테스트: 잔액, 스냅샷, 검증, 통계, CSV, AI 프롬프트
- Repository 테스트: 사용자 소유권, 정렬, 멱등성 조회
- 통합 테스트: 동일 사용자 동시 지출 후 최종 잔액과 스냅샷 연결
- H2 프로파일: 개발자의 PostgreSQL과 독립적으로 반복 실행

H2는 빠른 피드백에는 적합하지만 PostgreSQL의 잠금 구현과 완전히 같지는 않습니다. `TransactionPersistenceContract`의 롤백·동시 지출·중복 요청 검증을 H2와 Testcontainers PostgreSQL 클래스에서 공통 실행합니다. `test`는 Docker 없이, `postgresTest`는 Docker 필수로 실행하며 CI는 둘 다 요구합니다. 현재 로컬 PostgreSQL 테스트는 Docker 부재로 초기화 실패했고 실제 PostgreSQL 실행 검증은 남아 있습니다.

## 10. 의도적으로 남겨 둔 한계

- 거래 목록은 소규모 데모 기준으로 조회하며 대량 데이터 페이지네이션은 아직 적용하지 않았습니다.
- AI 호출은 동기식입니다. 사용량이 늘면 작업 상태와 재시도·월별 캐시가 필요합니다.
- CSV 가져오기는 중복 제외, 카테고리 생성, 시간순 스냅샷 정책을 정한 후 구현해야 합니다.
- 추가된 PostgreSQL Testcontainers 테스트와 Compose 기동을 Docker 환경에서 실행 검증해야 합니다.

컨테이너 구조, 잠금과 멱등성의 쉬운 설명, 테스트 독립성은 [백엔드 보완 기록](BACKEND_IMPROVEMENTS.md)을 참고하세요.

한계를 숨기기보다 현재 요구 규모에서 선택한 이유와 확장 시점을 설명하는 것이 이 프로젝트의 설계 원칙입니다.
