# Budget Buddy

[![CI](https://github.com/cheerio31608/budget-buddy-portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/cheerio31608/budget-buddy-portfolio/actions/workflows/ci.yml)

거래를 기록하고, 월별 소비 흐름을 살펴보는 가계부입니다.

처음에는 백엔드에서 거래와 잔액을 안전하게 다루는 데 집중했습니다. 같은 사용자의 요청이 겹치거나 네트워크 문제로 같은 거래가 다시 전송돼도 잔액이 틀어지지 않도록 트랜잭션, 비관적 락, 멱등 키를 적용했습니다. 이후 직접 사용해 볼 수 있도록 React 화면과 회원 인증을 붙이고, CSV 분석과 Gemini 소비 리포트까지 연결했습니다.

합계와 통계는 Java에서 계산하고, Gemini는 결과를 읽기 쉬운 말로 설명합니다. 서비스 배포와 실제 Gemini API 호출은 아직 확인 중입니다. 실제 금융 데이터 대신 가상 데이터로 체험해 주세요. 이 서비스는 결제·투자 조언을 제공하지 않습니다.

## Features

- 이메일 회원가입/로그인, BCrypt 해싱, JWT 인증, 로그아웃 시 기존 토큰 무효화
- 사용자별 거래·카테고리·AI 리포트 격리
- 수입/지출 등록, 검색·기간·카테고리 필터, 잔액과 등록 전후 스냅샷
- 월별 소비 분석·전월 비교·카테고리 지출·최근 거래
- CSV 헤더 자동 매핑·재사용 → 모호한 컬럼만 확인 → 임시 분석 또는 확인 후 DB 가져오기
- Gemini 월간/CSV 리포트 생성·저장·조회, 사용자별/전체 일일 생성 제한
- Flyway 마이그레이션, H2/실제 PostgreSQL 테스트 구성, 자동 빌드 CI

거래 수정·삭제는 제공하지 않습니다. 잔액 스냅샷을 보존하는 정정/취소 모델을 먼저 설계할 예정입니다. 이메일 인증·비밀번호 재설정·refresh token도 아직 없습니다.

## Architecture

```text
React + Vite + TypeScript (Vercel)
    → HTTPS REST + Authorization: Bearer JWT
Spring Boot (Render / any Docker host)
    → Controller → Service → Repository → PostgreSQL (Supabase / any PostgreSQL)
    → AnalysisService → GeminiClient → Google Gemini

DB 거래 → AnalysisTransaction → AnalysisService → Dashboard / Gemini
CSV → Parsing / Mapping / Validation ─┘
CSV 저장 → CsvImportService → 기존 TransactionService → 잔액·스냅샷·거래
```

프론트엔드는 DB나 Gemini에 직접 접속하지 않습니다. Supabase는 PostgreSQL 호스팅으로만 사용합니다. Supabase Auth/Data API에 의존하지 않습니다.

```text
budget-buddy/
  src/main/java/com/finance/budget_buddy/
    controller/    인증된 사용자를 Service에 전달
    dto/           API 입출력·공통 분석 모델
    security/      JWT·기존 Principal·인증 요청 제한
    service/       거래·분석·회원가입·AI 한도
      csv/         기존 파싱/검증 + 원자적 CSV 저장
    repository/    JPA 및 AI 사용량 JDBC
    entity/        User / Category / Transaction / AiReport
  src/main/java/db/migration/          seed ID 보정·PostgreSQL RLS
  src/main/resources/db/migration/    Flyway SQL
  src/main/resources/static/          보존된 로컬 전용 기존 화면
  src/test/                           기존 테스트 + 웹 기능 테스트
  Dockerfile
frontend/                  React 앱·API·타입·테스트
compose.yaml               로컬 PostgreSQL + Spring Boot
render.yaml                선택적 Render Blueprint
.github/workflows/ci.yml
```

## Tech Stack

| 영역 | 기술과 선택 이유 |
|---|---|
| Frontend | React 19, Vite 8, TypeScript 7. 화면 단위 컴포넌트와 API 타입. Redux/라우터/대형 UI 라이브러리 없이 상태 기반 메뉴 전환 |
| Charts | CSS와 HTML meter. 현재 필요한 비율·추세만 구현해 추가 차트 의존성 없음 |
| Backend | Java 17, Spring Boot 3.5.13, Spring Web, Bean Validation, Spring Data JPA |
| Security | Spring Security Resource Server/Nimbus JWT. 자체 서명 구현 대신 검증된 라이브러리. BCrypt는 기존 PasswordEncoder 재사용 |
| Database | PostgreSQL, H2, Flyway. 실제 저장은 PostgreSQL, 빠른 회귀 검증은 H2 |
| AI | Gemini REST API, Spring RestClient, 기본 gemini-2.5-flash, URL 교체 가능 |
| Test | JUnit 5, Mockito, MockMvc, Testcontainers 1.21.4, Vitest, Testing Library |
| Deployment | Vercel SPA, Render Docker, Supabase PostgreSQL. 일반 HTTP/JDBC와 환경변수만 사용 |

Docker는 실행 환경을 이미지로 묶는 도구, Compose는 앱·DB 컨테이너를 함께 실행하는 설정입니다. CI는 변경마다 빌드·테스트, CD는 배포까지 자동화하는 과정입니다. GitHub Actions는 **CI만** 수행합니다.

## Local Setup

### 1. 가장 간단한 실행: H2 + React

준비: Java 17, Node.js 22.12 이상, npm. 저장소 루트에서 **터미널 두 개**를 엽니다.

터미널 A — 백엔드:

```powershell
cd .\budget-buddy
.\gradlew.bat bootRun --args="--spring.profiles.active=h2 --server.address=127.0.0.1"
```

터미널 B — 프론트엔드:

```powershell
cd .\frontend
npm ci
if (-not (Test-Path .env.local)) { Copy-Item .env.example .env.local }
npm run dev
```

1. [http://localhost:5173](http://localhost:5173)에 접속합니다. CORS 기본값에 맞춰 127.0.0.1 대신 **localhost**를 사용하세요.
2. 회원가입으로 자신의 Budget Buddy 계정을 만듭니다. 비밀번호는 10자 이상, UTF-8 72바이트 이하입니다.
3. 거래 내역에서 수입을 먼저 등록한 뒤 지출을 등록합니다. 시작 잔액 0원, 음수 잔액 금지입니다.
4. 대시보드·CSV·AI 리포트를 확인합니다. Gemini 키가 없으면 AI 생성만 503 안내가 나오고 다른 기능은 작동합니다.

H2 데이터는 백엔드 종료 시 사라집니다. 각 터미널에서 Ctrl+C로 종료합니다. Gradle의 80% EXECUTING은 서버가 요청을 기다리는 정상 상태입니다. JWT는 메모리에만 보관하므로 새로고침/만료(기본 30분) 시 재로그인합니다.

기존 로컬 화면도 [localhost:8080](http://localhost:8080)에 남겨두었습니다. user1@test.com / password는 **로컬 전용 데모 계정**이며 prod에서는 로그인할 수 없습니다. 새 웹 앱에서는 회원가입을 권장합니다.

### 2. 로컬 PostgreSQL

전용 빈 데이터베이스를 만든 뒤 백엔드 터미널에서 설정합니다. 아래 값은 자리표시자입니다.

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/budget_buddy_portfolio"
$env:DB_USERNAME="your-database-user"
$env:DB_PASSWORD="your-local-database-password"
# 선택: 키 없이도 AI 이외 기능은 실행됩니다.
$env:GEMINI_API_KEY="your-gemini-api-key"
cd .\budget-buddy
.\gradlew.bat bootRun --args="--spring.profiles.active=local --server.address=127.0.0.1"
```

프론트엔드는 위 터미널 B와 같습니다. **DB 계정은 서버 접속용이며 웹 로그인 계정이 아닙니다.** Spring은 .env를 자동으로 읽지 않습니다. 환경변수는 설정한 터미널에서 실행한 프로세스에 전달됩니다.

### 3. Docker Compose

Docker Desktop을 Linux 컨테이너 모드로 실행하고 저장소 루트에서:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
# DB_PASSWORD에 자신의 로컬 비밀번호를 입력한 뒤 저장
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs --tail=50 app
```

백엔드 localhost:8080, DB localhost:5433. 프론트엔드는 frontend/에서 npm ci와 npm run dev로 별도 실행합니다. Compose는 로컬용이며 prod 프로필이 아닙니다.

docker compose down으로 종료하면 volume은 남습니다. **down -v는 DB를 삭제하므로 일반 종료에 사용하지 않습니다.** .env만 바꿔도 기존 DB 계정 비밀번호가 자동 변경되지는 않습니다. config는 비밀번호를 출력할 수 있어 --quiet를 사용하세요.

## Environment Variables

실제 Secret은 프론트엔드나 Git에 넣지 않습니다. 루트 .env.example, frontend/.env.example에는 안전한 예시만 있습니다.

| 이름 | 실행 위치 / 의미 | 기본값 또는 조건 |
|---|---|---|
| SPRING_PROFILES_ACTIVE | Backend 프로필 | local / h2 / compose / **prod** |
| DB_URL | Backend JDBC URL | prod 필수. 클라우드 SSL 사용 |
| DB_USERNAME / DB_PASSWORD | Backend DB 인증 | prod 필수 |
| JWT_SECRET | Backend HS256 서명 키 | prod 필수, 무작위 32바이트 이상. 로컬 미설정 시 실행마다 랜덤 키 |
| JWT_TTL_SECONDS | JWT 유효기간 | 1800, 허용 60~3600 |
| GEMINI_API_KEY | Backend Google Gemini 키 | 선택, 없으면 AI만 사용 불가 |
| GEMINI_API_URL | Backend 모델 endpoint | 기본 gemini-2.5-flash generateContent |
| FRONTEND_ORIGIN | Backend CORS 출처 | local: http://localhost:5173, prod: 명시적 HTTPS 주소 필수 |
| AI_DAILY_USER_LIMIT | 사용자별 하루 생성 시도 | 3 |
| AI_DAILY_GLOBAL_LIMIT | 전체 사용자 합산 하루 시도 | 30, 0은 AI 생성 중단 |
| AI_COOLDOWN_SECONDS | 사용자 재생성 대기 | 60 |
| PORT | Backend 수신 포트 | prod 기본 8080, Render 값 사용 |
| TZ | 서버/JVM 시간대 | 배포 예시 Asia/Seoul. 기존 LocalDateTime 유지 |
| VITE_API_BASE_URL | **Frontend 공개 설정** | 로컬 http://localhost:8080, production HTTPS URL 필수 |
| DB_NAME / DB_PORT / APP_PORT | 로컬 Compose 전용 | .env.example 참조 |

Origin에는 끝 /나 경로를 붙이지 않습니다. 필요하면 주소를 쉼표로 나눕니다. 와일드카드는 거부합니다. **VITE_ 접두사 값은 빌드에 공개됩니다.** Gemini 키·JWT Secret·DB 비밀번호를 절대 넣지 마세요.

## Deployment: Supabase → Render → Vercel

코드는 공개 GitHub 저장소에 올라가 있지만 웹 서비스는 아직 배포되지 않았습니다. Supabase·Render·Vercel의 계정, 요금제, Secret 설정은 배포 전에 직접 확인하세요.

### 1. Supabase PostgreSQL

1. Budget Buddy 전용 프로젝트/DB를 준비합니다. 개인 데이터가 있는 DB에 바로 연결하지 마세요.
2. Connect의 **Session pooler / 포트 5432** 연결정보를 확인합니다. IPv4 환경에서도 JDBC로 사용하기 쉽습니다. Transaction pooler(6543) 대신 Session 모드를 사용합니다.
3. Render에 아래 세 변수를 나누어 설정합니다.

```text
DB_URL=jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require
DB_USERNAME=<pooler-user-shown-in-dashboard>
DB_PASSWORD=<database-password>
```

URL에 비밀번호를 넣지 않습니다. username은 대시보드의 프로젝트 접미사를 포함한 값을 사용합니다. TLS 서버 신원 검증까지 강화하려면 인증서 설정과 sslmode=verify-full을 추가로 검토하세요.

첫 시작에 Flyway V1~V8이 적용됩니다. V8은 앱 테이블 RLS를 활성화하고 public 정책은 만들지 않습니다. Supabase anon/authenticated 역할이 Spring 인증을 우회해 테이블을 읽지 못하게 합니다. **백엔드는 테이블을 생성한 owner 역할로 연결**해야 합니다. 후속으로 최소 권한 역할을 분리하면 RLS 정책도 함께 설계해야 합니다.

prod는 자동 baseline을 하지 않습니다. 스키마가 있는데 Flyway 이력이 없다면 백업/스키마 대조부터 하세요. 임의 clean, repair, 이력 삭제는 금지합니다. 이번 마이그레이션에서 기존 데이터는 삭제하지 않습니다.

### 2. Render Backend

1. 공개 저장소의 `main` 브랜치를 연결해 Web Service를 만듭니다.
2. Runtime **Docker**, Root Directory **budget-buddy**, Dockerfile **./Dockerfile**, Build context **.**.
3. SPRING_PROFILES_ACTIVE=prod, DB 3개 값, JWT_SECRET, FRONTEND_ORIGIN, TZ=Asia/Seoul을 설정합니다. AI를 쓸 때 GEMINI_API_KEY도 설정합니다.
4. JWT_SECRET은 안전한 랜덤 값(32바이트 이상)을 사용합니다. Blueprint의 generateValue도 가능합니다. 여러 서버는 같은 키를 공유합니다. 변경하면 기존 토큰은 무효화됩니다.
5. FRONTEND_ORIGIN은 Vercel에서 예약/확인한 프론트엔드 HTTPS 주소입니다.
6. Health Check Path **/api/health**. Dockerfile이 bootJar 빌드와 java -jar /app/app.jar 실행을 처리하므로 별도 build/start 명령은 필요 없습니다. 앱은 비root 사용자로 실행됩니다.
7. https://<backend>/api/health 응답이 {"status":"UP"}인지 확인합니다. liveness이며 실시간 DB/Gemini 상태 검사까지 하지는 않습니다.

루트 render.yaml은 선택적 Blueprint입니다. 적용 시 자원 생성/비용 가능성이 있으므로 직접 요금제를 확인하세요. 자동 DB 생성이나 배포 Secret은 넣지 않았습니다.

### 3. Vercel Frontend

1. 같은 GitHub 저장소를 Import하고 배포 브랜치를 `main`으로 지정합니다.
2. Root Directory **frontend**, Framework **Vite**, Install **npm ci**, Build **npm run build**, Output **dist**, Node **22.x 이상**.
3. VITE_API_BASE_URL=https://<render-backend>를 넣습니다. /api 접미사는 붙이지 않습니다.
4. Vercel 주소를 Render의 FRONTEND_ORIGIN에 정확히 등록하고 백엔드를 재시작합니다. Preview 주소는 각각 명시적으로 추가합니다.
5. frontend/vercel.json이 SPA 새로고침 경로를 index.html로 연결합니다. 현재 메뉴는 상태 기반이고 새로고침하면 로그인 화면으로 돌아갑니다.
6. Vite 변수는 빌드 시점에 반영됩니다. 변경 후 재배포하세요. Vercel에는 DB/Gemini/JWT Secret을 설정하지 않습니다.

공개 전 A/B 사용자 격리, 데모 API 차단, 실제 CORS/HTTPS, Supabase RLS, 백업을 확인하세요. Gemini 프로젝트 자체 쿼터/예산 설정도 적용하세요. 앱 일일 한도는 비용 보장의 대체물이 아닙니다.

## Tests & Build

```powershell
cd .\budget-buddy
.\gradlew.bat build
node --test frontend-tests/transaction-request.test.mjs
# Docker가 실행 중일 때 실제 PostgreSQL 검증
.\gradlew.bat postgresTest
cd ..\frontend
npm ci
npm test
$env:VITE_API_BASE_URL="https://your-backend.example.com"
npm run build
```

build는 Java 단위/H2/API 테스트와 JAR, postgresTest는 Testcontainers의 독립 PostgreSQL을 검증합니다. 기존 테스트를 삭제·비활성화하지 않았습니다. Docker가 없으면 postgresTest는 **실패**합니다. H2 성공이 PostgreSQL 검증을 대신하지 않습니다.

회원가입/해싱·로그인·JWT 만료/서명·로그아웃·미인증 차단·타인 데이터 접근·CORS·CSV 저장/재시도/롤백·AI 한도/동시 요청을 검증합니다. Gemini는 mock이므로 실제 비용이 발생하지 않습니다.

Actions는 Java build postgresTest → 기존 Node 테스트 → Compose 기동 확인과 별도 React npm ci → test → production build를 수행합니다. 원격 CI는 push 후 실제 결과를 확인해야 합니다.

**로컬 검증(2026-09-23):** Java/H2/API 테스트 및 Backend build, React 테스트·production build, 브라우저 로그인·거래 저장·잔액 반영 확인. Docker 부재로 PostgreSQL 전용 2개 suite는 초기화 실패했습니다. Compose·실제 PostgreSQL·원격 CI 검증은 남아 있습니다. HTML 결과는 budget-buddy/build/reports/tests/에 생성됩니다.

## 트랜잭션·동시성·멱등성

기존 TransactionService는 한 트랜잭션에서 사용자 행 비관적 락 → 중복 키 → 카테고리 소유자/잔액 검증 → 잔액 변경 → 스냅샷/거래 저장을 처리합니다. 같은 사용자의 동시 지출은 순서대로 처리합니다. 낙관적 락은 충돌 후 재시도 정책이 필요해 기존 비관적 락을 유지했습니다. DB CHECK/FK/UNIQUE는 서비스 검증을 보완합니다.

멱등성은 같은 요청을 반복해도 효과가 한 번만 생기는 성질입니다. 저장됐지만 응답을 잃은 브라우저가 같은 idempotencyKey/내용으로 재시도하면 기존 거래를 반환합니다. 다른 내용으로 키를 재사용하면 409입니다. 키를 생략한 외부 API 요청은 중복을 막지 못합니다.

CSV는 전체 파일을 한 트랜잭션으로 저장하며 기존 거래 서비스를 호출합니다. 없는 카테고리는 자신의 소유로 생성합니다. 중간 실패 시 앞선 거래·카테고리·잔액도 롤백합니다. 정규화 데이터 SHA-256 + 행 번호로 키를 만들어 같은 파일 재전송을 건너뜁니다. **편집·재정렬 파일은 새 업로드**이고, 파일 내 중복 의심 행은 확인 후 그대로 저장합니다.

스냅샷은 발생일이 아닌 **장부 등록 순서**입니다. 과거 거래를 추가해도 기존 스냅샷은 재계산하지 않습니다. CSV도 파일 순서대로 처리하므로 수입이 지출보다 먼저 있어야 합니다.

## CSV 형식

```csv
date,description,category,amount,type
2026-01-01,월급,급여,3000000,INCOME
2026-01-02,동네마트,식비,35000,EXPENSE
```

한국어 헤더와 사용자 매핑, 쉼표/탭/세미콜론, UTF-8/MS949, 따옴표 안 구분자를 지원합니다. 날짜·카테고리·금액·구분은 필수입니다. 금액은 소수 두 자리까지이며 음수/괄호는 절댓값으로 정규화하고 **구분 컬럼으로 방향을 결정**합니다. 지수 표기·세 자리 소수·미래/존재하지 않는 날짜·빈 행·열 개수 불일치는 거절합니다.

파일 5MB(전체 multipart 6MB), 분석 5,000행, 저장 500행, 50열, 셀 2,000자 제한. 임시 분석은 정상 행만 집계하지만 DB 저장은 오류 행이 하나라도 있으면 전체 거절합니다. 원본 파일은 영구 보관하지 않습니다.

## API

| Method / Path | 역할 |
|---|---|
| POST /api/web-auth/register · /login | 회원가입 / 로그인, JWT 발급 |
| GET /api/web-auth/me | 현재 사용자 |
| POST /api/web-auth/logout | 사용자의 모든 기존 JWT 무효화 |
| GET /api/categories | 내 카테고리 |
| GET / POST /api/transactions | 내 거래 조회 / 등록 |
| GET /api/transactions/{id} | 내 거래 단건, 타인은 404 |
| GET /api/analysis/monthly?month=2026-01 | 선택 월, 생략 시 이번 달 |
| GET /api/analysis/dashboard | 기존 이번 달 API 유지 |
| GET /api/analysis?start=2026-01-01&end=2026-01-31 | 기간 분석 |
| POST /api/csv/preview | file multipart, 미리보기 |
| POST /api/csv/analyze · /import · /ai-report | file + JSON mapping multipart |
| POST /api/ai/report/monthly?month=2026-01 | 월간 AI 생성/저장 |
| GET /api/ai/reports | 내 저장 리포트 |
| GET /api/health | 인증 없는 최소 liveness |

회원가입·로그인·health 외 사용자 데이터 API는 Authorization: Bearer <accessToken>이 필요합니다. 전달받은 userId는 소유자로 사용하지 않습니다. 가상 로컬 계정 API 예시:

```powershell
$body = @{ email="your-test-email@example.com"; password="your-test-password" } | ConvertTo-Json
$auth = Invoke-RestMethod http://localhost:8080/api/web-auth/register -Method Post -ContentType application/json -Body $body
$headers = @{ Authorization="Bearer $($auth.accessToken)" }
Invoke-RestMethod http://localhost:8080/api/categories -Headers $headers
Invoke-RestMethod http://localhost:8080/api/analysis/dashboard -Headers $headers
Invoke-RestMethod http://localhost:8080/api/web-auth/logout -Method Post -Headers $headers
```

에러 형식은 {code,message,status}. 400 입력/잔액 문제, 401 인증 실패, 403 접근 불가, 404 없는 본인 데이터, 409 중복/충돌, 429 사용량, 502 Gemini 실패, 503 Gemini 미설정, 500 내부 오류입니다. SQL/stack trace/토큰/비밀번호는 응답에 노출하지 않습니다. Swagger는 로컬만 제공됩니다.

## 보안·운영 범위

- JWT HS256 고정, issuer/audience/만료 검증. DB tokenVersion을 매 요청 확인해 로그아웃을 적용하므로 완전 무상태 인증은 아닙니다.
- prod는 세션 인증/기존 화면/데모 API를 막고 헤더 JWT만 사용합니다. 자동 전송 쿠키를 인증에 쓰지 않아 CSRF는 끕니다. 기존 로컬 세션은 CSRF 유지.
- AI 한도는 DB에 먼저 예약하여 동시/다중 인스턴스 요청도 제한합니다. Gemini 원격 호출 실패는 시도 횟수에 포함되지만, 로컬 API Key 미설정은 예약 전에 안내합니다. Gemini 호출은 DB 트랜잭션 밖, 연결 5초/읽기 40초, 출력 최대 4,096토큰.
- 원본 전체 대신 집계(사용처/카테고리 포함)가 Google로 전달됩니다. 입력 안 명령을 따르지 않도록 프롬프트를 작성하고, 결과는 HTML이 아닌 텍스트로 표시합니다.
- 로그인 제한은 인스턴스 메모리/IP 기준 30회/분. 프록시 뒤에서는 같은 IP로 보일 수 있습니다. 전달 헤더를 무조건 신뢰하지 않습니다.
- 이 공개 저장소는 기존 비공개 저장소의 Git 이력을 복사하지 않고 최신 파일로 새로 시작했습니다. 로컬 `.env`는 포함하지 않았습니다. **Secret 노출이 의심되면 해당 키를 폐기하고 재발급하세요.**

## 복습·다음 단계

[웹 전환 설계 기록](budget-buddy/docs/WEB_DEPLOYMENT.md)에 핵심 코드·변경 이유·면접 질문을 정리했습니다. [개발 중 의사결정·트러블슈팅 기록](budget-buddy/docs/PORTFOLIO_DECISIONS.md)에는 CSV 자동 매핑과 Docker 개발환경을 고민한 배경을 남겼습니다. [기존 백엔드 학습 가이드](budget-buddy/docs/BACKEND_IMPROVEMENTS.md)도 보존했습니다.

다음 순서: Docker/실제 PostgreSQL·배포 smoke test → 이메일 인증/재설정·edge rate limit → 페이지네이션/대량 집계 → 토큰 갱신 → 정정 거래 모델 → 모니터링·백업 복구. 현재 한도는 무제한 Gemini 비용을 줄이지만 자동 가입/DoS를 완전히 막지는 않습니다.

## 주요 화면

배포 후 로그인/회원가입, 대시보드, 거래 내역, CSV 검증/가져오기, AI 리포트 스크린샷을 여기에 추가하세요.

## 공식 참고 자료

- [Spring Security JWT](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html)
- [Supabase Spring Boot 연결](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot)
- [Render Docker 배포](https://render.com/docs/docker)
- [Vercel Vite 배포](https://vercel.com/docs/frameworks/frontend/vite)
- [Gemini 2.5 Flash](https://ai.google.dev/gemini-api/docs/models/gemini-2.5-flash)
