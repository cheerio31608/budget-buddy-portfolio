# 백엔드 보완 기록과 학습 가이드

## 작업 전 확인한 상태 (2026-09-18)

- Java toolchain 17, Spring Boot 3.5.13, Gradle Wrapper 8.14.4. 이 PC의 Java는 17.0.12.
- Controller → Service → Repository 구조. users, categories, transactions, ai_reports를 JPA/Flyway로 관리.
- 거래 등록·목록·단건 조회, 세션 로그인, DB/CSV 공통 통계, Gemini 리포트 구현.
- 거래 **수정·삭제 API는 없음**. CSV는 임시 분석만 지원.
- 등록은 사용자 비관적 락 → 멱등키 확인 → 카테고리 소유권/유형 확인 → 잔액 변경 → 스냅샷 저장을 하나의 트랜잭션으로 처리.
- 금액은 BigDecimal, 잔액 음수/범위 초과와 미래 거래를 거절. DB에도 CHECK, FK, UNIQUE 제약이 있음.
- 세션·BCrypt·CSRF·Principal 소유권 검증 적용. DB 계정은 웹 로그인 계정과 다름.
- DB는 환경변수로 연결. `.env`와 `application-local.properties`는 Git에서 제외. Flyway 데모 계정은 공개된 로컬 체험용 계정이므로 인터넷 운영용으로 사용할 수 없음.
- 작업 시작 시 바깥 저장소는 깨끗했고 HEAD는 `d59b37c`. 안쪽 `budget-buddy/.git`에는 다른 이력을 기준으로 기존 변경이 표시됨. 파일/이력을 삭제하거나 reset하지 않았음.
- 기존 테스트 49개는 H2 중심. 기존 CI도 H2 테스트만 실행.

## 우선순위와 변경 단위

| 순서 | 변경 파일 | 이유 | 검증/실행 |
|---|---|---|---|
| 1 | 루트 compose.yaml, .env.example / 모듈 Dockerfile, .dockerignore, application-compose.properties / DemoDataController, DemoDataService | DB 설치 차이와 앱 시작 순서를 줄이고 로컬 데모를 Compose에서도 제공 | `docker compose up --build -d`; 기존 `test bootJar` |
| 2 | TransactionService, GlobalExceptionHandler, ErrorCode, application.properties, app.js, transaction-request.mjs 및 관련 테스트 | 재시도 키 유지, 날짜 정밀도, 안전한 충돌 응답 | `gradlew test bootJar`, Node 요청 테스트 |
| 3 | build.gradle, TransactionPersistenceContract, H2/PG 구현 클래스, 기존 동시성 테스트 | 롤백/동시 요청/중복 요청을 실제 저장 결과로 검증 | `gradlew test`, `gradlew postgresTest` |
| 4 | .github/workflows/ci.yml, README, 학습 문서 | 새 환경에서도 빌드·테스트·Compose 시작을 재현 | push/PR CI, 문서 명령 검토 |

기존 계층과 DB 제약을 유지했습니다. 새 운영 시스템이나 Redis는 추가하지 않았습니다. 각 행은 별도 커밋으로 검토할 수 있는 단위이며 자동 커밋·push·배포는 수행하지 않습니다.

## Docker: 무엇인지 → 왜 필요한지 → 어떻게 사용하는지

Docker는 프로그램과 실행에 필요한 환경을 컨테이너로 실행하는 도구입니다. 이미지가 실행 준비물이라면 컨테이너는 그것을 실제로 실행한 인스턴스입니다.

개발자마다 PostgreSQL 설치와 포트가 다르면 실행 안내를 따라도 실패할 수 있습니다. Compose는 앱과 DB의 이미지, 환경변수, 네트워크, 시작 순서를 YAML 파일 하나로 정의합니다.

이 프로젝트는 PostgreSQL 16과 Java 17 앱을 실행합니다. DB healthcheck가 성공해야 앱이 시작하며, Flyway가 V1~V5를 적용하고 JPA가 스키마를 검증합니다. 앱 컨테이너의 `localhost`는 앱 자신이므로 DB 주소는 `db:5432`입니다. PC에서 DB에 접속할 때는 기본 `localhost:5433`을 사용합니다.

DB 데이터는 named volume에 남습니다. `docker compose down`으로 컨테이너를 내려도 데이터는 유지됩니다. `down -v`는 데이터를 지우므로 일반 종료 명령에 사용하지 않습니다. DB 비밀번호 환경변수를 바꿔도 기존 볼륨의 DB 비밀번호가 자동 변경되지는 않습니다.

Dockerfile은 JDK로 JAR을 만드는 단계와 JRE로 실행하는 단계를 나눕니다. 실행 이미지는 일반 사용자로 실행하고, `.dockerignore`가 로컬 비밀 설정을 빌드 입력에서 제외합니다. Compose 파일은 로컬 데모용이며 호스트 포트를 127.0.0.1에 바인딩합니다. 이미지 태그는 Java 17/PostgreSQL 16 계열이며 패치 이미지까지 고정한 환경은 아닙니다. 엄밀한 이미지 고정이 필요하면 실행 검증 후 digest를 고정합니다.

## 트랜잭션과 락: 무엇인지 → 왜 필요한지 → 어떻게 동작하는지

트랜잭션은 여러 DB 작업을 하나의 성공/실패 단위로 묶습니다. 거래만 저장되고 잔액 변경이 실패하면 서로 다른 숫자가 보이므로, `TransactionService.createTransaction`에서 둘을 한 번에 커밋하거나 롤백합니다.

`@Transactional`만으로 모든 동시 요청이 순서대로 실행되는 것은 아닙니다. 잔액 10,000원에 7,000원 요청 두 개가 동시에 오면 둘 다 기존 잔액을 읽을 수 있습니다. `UserRepository.findByIdForUpdate`가 사용자 행을 잠그면 첫 요청이 끝난 뒤 두 번째 요청이 3,000원을 읽고 지출을 거절합니다.

| 방식 | 동작 | 이 프로젝트의 선택 |
|---|---|---|
| 낙관적 락 | 버전 번호로 다른 요청의 선행 변경을 감지하고 충돌 시 거절/재시도 | 충돌 복구 정책이 추가로 필요해 현재는 도입하지 않음 |
| 비관적 락 | 변경할 사용자 행을 먼저 잠그고 짧게 순차 처리 | 기존 구조 유지. 같은 사용자의 잔액 변경에 적용 |
| DB 제약조건 | 음수 잔액, 중복 키, 잘못된 소유권 등을 DB가 거절 | 락을 보완하는 최종 방어선으로 함께 사용 |

Compose의 PostgreSQL 연결에는 `lock_timeout=5s`를 설정합니다. 잠긴 행을 무한정 기다리는 대신 충돌 응답을 받고 같은 멱등키로 재시도할 수 있습니다. 직접 설치한 DB를 쓰는 local 프로필에는 이 설정이 자동 적용되지 않습니다. 필요하면 DB/연결 설정으로 동일 정책을 적용하세요. 락을 잡은 상태에서 Gemini를 호출하지 않으며, Gemini 응답 저장은 짧은 별도 DB 트랜잭션입니다.

스냅샷은 거래 발생일이 아니라 **장부에 반영한 순서**의 잔액입니다. 기존 거래를 수정하거나 삭제하면 이력이 불명확해지므로 해당 API는 추가하지 않았습니다. 다음 단계에서 원본 거래를 참조하는 취소/정정 거래, 잔액 부족 처리, 중복 취소 방지를 먼저 설계해야 합니다.

## 멱등성: 무엇인지 → 왜 필요한지 → 어떻게 동작하는지

같은 작업을 다시 요청해도 최종 결과가 한 번 요청했을 때와 같은 성질입니다. 저장은 성공했는데 네트워크 문제로 응답이 안 보이면 사용자는 저장 버튼을 다시 누릅니다. 이때 거래가 두 번 저장되면 안 됩니다.

1. 화면에서 새 거래의 멱등키를 만듭니다.
2. 응답을 못 받은 동일 입력의 재시도는 같은 키와 내용을 보냅니다.
3. 서버는 사용자 행을 잠근 뒤 `(user_id, idempotency_key)`를 조회합니다.
4. 기존 내용과 같으면 기존 거래를 반환하고 잔액을 다시 바꾸지 않습니다.
5. 같은 키인데 금액/카테고리/메모/시각 등이 다르면 T004/409로 거절합니다.
6. DB UNIQUE 제약도 중복 키 저장을 막습니다.

키는 기존 API 호환성을 위해 선택 항목입니다. **키 없는 요청에는 중복 방지를 보장하지 않습니다.** 화면은 키를 자동 전송하지만 메모리에만 유지합니다. 새로고침·창 닫기·입력 수정 후에는 이전 요청과 자동 연결되지 않으므로 응답이 불확실하면 먼저 거래 목록을 확인해야 합니다.

Java는 나노초, PostgreSQL timestamp는 마이크로초 정밀도를 사용합니다. 저장 전에 마이크로초로 정규화해 저장 과정의 반올림 때문에 동일 재시도가 충돌하는 문제를 막았습니다.

## 테스트 자동화: 무엇인지 → 왜 필요한지 → 어떻게 동작하는지

테스트 자동화는 사람이 화면을 반복 클릭하는 대신 코드가 기대 결과를 확인하는 것입니다. 단위 테스트는 Service 계산을 빠르게 확인하고, 통합 테스트는 Spring과 DB를 함께 실행해 실제 저장 결과를 확인합니다.

H2는 빠르고 Docker가 없어도 실행할 수 있습니다. 하지만 PostgreSQL 호환 모드도 실제 PostgreSQL 엔진은 아닙니다. Testcontainers 1.21.4는 테스트용 PostgreSQL 컨테이너를 띄우고 임의 포트/계정을 Spring 테스트에 연결합니다. 종료 시 컨테이너를 정리하며 개인 PostgreSQL과 Compose 볼륨에는 연결하지 않습니다.

추가 의존성은 테스트 전용 `org.testcontainers:junit-jupiter`와 `postgresql`입니다. 기존 Spring Boot가 관리하는 버전을 사용합니다. 대안인 수동 PostgreSQL 설치는 테스트 데이터 초기화와 포트 관리가 필요하고, H2만으로는 실제 DB 차이를 확인할 수 없어 이 조합을 선택했습니다. 웹 재시도 테스트는 Node 내장 테스트 도구를 사용하므로 npm 라이브러리는 추가하지 않았습니다.

`TransactionPersistenceContract`의 같은 검증을 H2와 PostgreSQL 클래스가 상속합니다. 각 테스트 전후에 전용 사용자/카테고리/거래를 초기화하고 현재 시각에 의존하지 않는 과거 거래일을 씁니다. 테스트 전체를 자동 롤백으로 감싸지 않아 서비스 커밋 결과를 직접 읽습니다.

- 정상 저장 후 잔액·스냅샷 확인
- flush로 SQL 전송 후 애플리케이션 예외 발생 → 둘 다 롤백
- flush 후 DB CHECK 위반 → 전체 작업 롤백
- 나노초가 있는 동일 요청 재시도 → 기존 거래 반환
- 다른 내용에 같은 키 → 충돌 및 잔액 불변
- 서비스 우회 중복 INSERT → DB UNIQUE 제약 확인
- 동시 초과 지출 2개 → 1개만 성공 (3회 반복)
- 같은 키 동시 요청 5개 → 거래 1개와 한 번의 차감 (3회 반복)

동시 테스트는 시작 신호를 맞추고 각 Future의 성공/실패를 확인합니다. 제한 시간과 스레드 종료 대기를 두어 작업 실패를 놓치거나 다음 테스트에 스레드가 남지 않도록 했습니다.

일반 `test`는 Docker 없이 실행하고 `postgresTest`는 Docker 필수입니다. 이 분리는 실패 테스트를 숨기기 위한 skip이 아니며, CI는 두 작업을 모두 실행해야 성공합니다. `@Disabled`, Docker 미설치 시 자동 skip은 사용하지 않습니다.

## 예외와 로그

응답 형식은 `{ "code": "C006", "message": "...", "status": 409 }`로 통일합니다.

| 상태 | 의미 | 예시 |
|---|---|---|
| 200 | 요청 처리 성공 | 신규 등록 또는 동일 키의 기존 결과 반환 (기존 계약 유지) |
| 400 | 입력/도메인 조건 불충족 | 금액 오류, 미래 거래, 잔액 부족 |
| 401 | 인증 필요 또는 실패 | 잘못된 로그인, 만료 세션 |
| 403 | 권한 또는 CSRF 검증 실패 | 토큰 없이 POST |
| 404 | 사용할 수 있는 데이터/경로 없음 | 본인 소유가 아닌 거래도 404로 처리 |
| 405 | 허용하지 않는 HTTP 메서드 | 리포트 생성에 GET 요청 |
| 409 | 현재 상태와 충돌 | T004 키 재사용 충돌, C006 동시성 충돌, C007 DB 제약 위반 |
| 500 | 예상하지 못한 내부 실패 | 안전한 일반 메시지만 응답 |
| 502/503 | 외부 AI 실패/미설정 | A003/A002 |

일반 예외와 DB 예외는 메시지에 요청값·SQL 행 내용이 들어갈 수 있습니다. 핸들러는 예외 종류/발생 위치 또는 오류 코드만 기록하고, Hibernate SQL 오류 상세 로깅도 끕니다. 원문 HTTP body, 비밀번호, 세션 쿠키, CSRF 토큰, API Key를 로그에 추가하지 않습니다. 이 선택은 안전한 로깅과 진단 상세 정보 사이의 절충이며, 운영 관측성은 향후 민감값 제거 규칙과 함께 보완해야 합니다.

## CI와 CD

CI(지속적 통합)는 코드 변경마다 빌드·테스트해 결함을 빨리 찾는 과정입니다. CD는 검증된 결과를 배포 가능한 상태로 만들거나 실제 서버에 배포하는 과정입니다. 이번 변경은 CI까지만 구성합니다.

GitHub Actions는 저장소의 워크플로를 읽어 임시 실행 환경에서 작업을 수행합니다. push 또는 main 대상 PR → 소스 내려받기 → Java 17/Gradle 준비 → build와 postgresTest → Node 재시도 테스트 → 임시 비밀번호 생성 → Compose 이미지 빌드/앱 시작 확인 → 테스트 리포트 보관 순서입니다. PostgreSQL 테스트가 실패하면 CI도 실패합니다. GitHub의 임시 Ubuntu 환경을 사용하며 클라우드 계정이나 배포 비밀키가 필요하지 않습니다.

## 검증 범위와 남은 일

2026-09-18 로컬 검증에서 일반 Java 테스트 64개와 실행 JAR 빌드, Node 재시도 테스트 3개는 통과했습니다. 새 프로세스에서 재실행해도 통과했습니다. 실행 JAR의 임시 H2 서버에서 로그인, 동일 요청 재전송 후 거래 1건·잔액 1회 반영, 새 JS 모듈 제공도 확인하고 서버를 종료했습니다. PostgreSQL 테스트 클래스는 컴파일됐지만 Docker 런타임이 없어 시작 단계에서 실패했습니다. 따라서 PostgreSQL 시나리오, Compose 이미지 빌드/기동, GitHub 원격 CI 성공은 **아직 확인되지 않았습니다**. Docker 설치 후 README 명령으로 확인하는 것이 최우선입니다.

그다음은 PostgreSQL 락 대기 실측, 수정/삭제 대신 취소·정정 모델, 페이지네이션, 로그인 시도 제한과 운영용 계정 생성 정책입니다. Redis/클라우드는 필요와 운영 범위를 정한 뒤 검토합니다.

## 참고한 공식 문서

- [Docker Compose 시작 순서와 healthcheck](https://docs.docker.com/compose/how-tos/startup-order/)
- [Compose 환경변수](https://docs.docker.com/compose/how-tos/environment-variables/)
- [Testcontainers JUnit 5 수명주기](https://java.testcontainers.org/test_framework_integration/junit_5/)
- [PostgreSQL timestamp 정밀도](https://www.postgresql.org/docs/16/datatype-datetime.html)
- [GitHub Actions와 Gradle](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle)
