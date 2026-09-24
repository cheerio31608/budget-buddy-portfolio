# 웹 서비스 전환 복습 노트

## 요청 흐름

React는 `VITE_API_BASE_URL`만 알고, 로그인 후 받은 JWT를 `Authorization: Bearer` 헤더에 붙입니다. Spring Security가 서명을 검증하고 DB의 `tokenVersion`도 확인한 뒤, Controller가 인증 Principal의 userId를 Service에 전달합니다. URL이나 JSON의 userId는 소유권 판단에 사용하지 않습니다.

## 핵심 코드

- `SecurityConfig`: local 세션/CSRF와 prod JWT/무상태 정책, CORS 허용 출처
- `JwtService`: issuer·audience·만료·서명·tokenVersion 검증
- `RegistrationService`: BCrypt 회원가입과 사용자 전용 기본 카테고리 생성
- `TransactionService`: 사용자 row 비관적 락, 잔액과 거래의 원자적 저장, 멱등성
- `AnalysisService`: DB와 CSV가 함께 쓰는 순수 통계 계산
- `CsvMappingService`: 컬럼 매핑과 행별 오류 수집
- `CsvImportService`: 검증된 CSV를 파일 전체 트랜잭션으로 기존 거래 Service에 전달
- `AiQuotaService`: DB row lock으로 AI 호출 시도 수 예약
- `AiReportService`: 집계값만 Gemini에 전달하고 결과를 `ai_reports`에 저장

## 설계 이유

금액 계산은 LLM에 맡기면 재현성과 정확성을 보장할 수 없으므로 Java `BigDecimal`로 계산합니다. Gemini는 총액·카테고리·전월 변화처럼 서버가 만든 사실을 자연어로 설명합니다. CSV와 DB를 같은 `AnalysisTransaction`으로 바꾼 뒤 같은 `AnalysisService`를 호출해 계산 규칙이 갈라지지 않게 했습니다.

기존 잔액 스냅샷을 보존하기 위해 거래 수정/삭제는 추가하지 않았습니다. 동시 지출은 사용자 row의 `PESSIMISTIC_WRITE` 잠금으로 직렬화합니다. 네트워크 재전송은 사용자별 `idempotencyKey`와 DB unique 제약으로 한 번만 반영합니다.

## CSV 처리

1. 브라우저가 preview를 요청하면 서버가 파일 크기·확장자·인코딩·구분자를 확인합니다.
2. 헤더 별칭을 자동 제안하고 사용자가 매핑을 확정합니다.
3. 각 행의 날짜·금액·구분·필수값을 검사해 행 번호별 오류를 반환합니다.
4. 임시 분석은 정상 행만 공통 분석기에 보냅니다.
5. DB 가져오기는 오류가 하나라도 있으면 저장하지 않고, 성공하면 파일 전체를 하나의 트랜잭션으로 저장합니다.

## Gemini 호출

`AiReportService`가 먼저 분석 결과의 요약값을 만들고 `AiQuotaService`가 사용량을 예약합니다. 이후 짧은 DB 트랜잭션 밖에서 `GeminiClient`가 환경변수 API key로 호출합니다. 실패한 시도도 quota를 소비합니다. 키는 프론트엔드 번들에 포함되지 않습니다.

## 면접 질문 예시

**왜 JWT인데 DB를 매 요청 조회하나요?** 로그아웃 즉시 기존 토큰을 차단하기 위해 tokenVersion을 비교합니다. 완전한 무상태성보다 이 프로젝트의 로그아웃 요구를 우선한 선택입니다.

**왜 낙관적 락이 아닌 비관적 락인가요?** 잔액 차감은 충돌 시 재시도보다 한 사용자 장부의 짧은 요청을 직렬화하는 편이 단순하고 안전합니다.

**AI가 금액을 계산하지 않는 이유는요?** 같은 입력에 같은 통계를 재현하고 금융성 숫자 오류를 줄이기 위해 계산은 서버가 담당합니다.

**CSV 재업로드는 어떻게 막나요?** 정규화된 거래 목록의 SHA-256과 행 번호를 멱등키로 만들어 같은 파일/순서의 행을 건너뜁니다. 파일을 수정하거나 재정렬하면 새 업로드로 취급합니다.

**RLS를 켰는데 Spring이 접근하는 이유는요?** Supabase public Data API 역할의 우회를 막되, 애플리케이션은 테이블 owner DB 계정으로 연결합니다. 최소 권한 계정으로 분리하려면 정책을 별도로 설계해야 합니다.

**Testcontainers가 필요한 이유는요?** H2는 빠르지만 PostgreSQL의 잠금·타입·제약 동작을 완전히 같게 흉내 내지 않습니다. CI에서는 실제 PostgreSQL 컨테이너를 추가로 실행합니다.

## 복습 순서

1. Spring Security filter chain과 JWT resource server
2. `@Transactional` 전파와 DB row lock
3. JPA 소유자 조건 쿼리와 unique 제약
4. Flyway versioned migration
5. Multipart CSV parsing/validation
6. React state, FormData, CORS
7. Docker image와 Render/Vercel 환경변수
