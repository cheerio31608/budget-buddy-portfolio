# Budget Buddy Next Steps

> **과거 개발 기록입니다.** 아래 항목 중 공통 분석 모델, 집계 기반 Gemini 프롬프트, `AiReport` 저장, CSV 임시 분석, 통합 테스트는 현재 이미 구현되었습니다. 최신 실행 방법과 현재 백로그는 [루트 README](README.md), 상세 구조는 [아키텍처 문서](budget-buddy/docs/ARCHITECTURE.md)를 기준으로 확인해 주세요.

오늘은 동시성 제어를 우선 해결했습니다. 아래 항목은 다음 작업 세션에서 이어서 진행할 백로그입니다.

## 1. QueryDSL 기반 AI 통계 쿼리 최적화

현재 `AiReportService`는 최근 30일 거래 원본을 문자열로 변환해 Gemini API에 전달합니다. 데이터가 많아지면 프롬프트가 길어지고 토큰 비용이 증가합니다.

다음 작업:

- `build.gradle`에 QueryDSL 의존성 추가
- `QTransaction`, `QCategory` 생성 설정 확인
- 최근 30일 카테고리별 지출 합계 DTO 생성
- 전월 동기 대비 지출 합계 DTO 생성
- `TransactionRepositoryCustom` 및 `TransactionRepositoryImpl` 구현
- Gemini 프롬프트 입력을 Raw Transaction 목록에서 통계 DTO로 변경

포트폴리오 어필 포인트:

- LLM 비용 최적화
- DB 집계와 애플리케이션 책임 분리
- 대량 거래 데이터 처리 관점의 설계

## 2. AI 리포트 비동기 처리 및 DB 캐싱

현재 AI 리포트 요청은 Gemini API 응답이 올 때까지 HTTP 요청을 붙잡습니다. 외부 API 지연이 길어지면 사용자 경험과 서버 처리량이 나빠질 수 있습니다.

다음 작업:

- `AiReport` 엔티티 추가
- `AiReportRepository` 추가
- `PENDING`, `COMPLETED`, `FAILED` 상태 관리
- `@EnableAsync` 및 AI 전용 Executor 설정
- 리포트 생성 요청 API와 리포트 조회 API 분리
- 같은 사용자/같은 월 리포트가 이미 있으면 Gemini API 재호출 없이 DB 결과 반환

포트폴리오 어필 포인트:

- 외부 API 지연 대응
- 캐싱을 통한 비용 절감
- 비동기 작업과 상태 기반 조회 API 설계

## 3. 테스트 보강

현재는 Mockito 기반 서비스 단위 테스트가 중심입니다. 동시성 문제는 실제 트랜잭션과 DB 락 동작이 중요하므로 통합 테스트가 있으면 설득력이 커집니다.

다음 작업:

- `@SpringBootTest` 또는 `@DataJpaTest` 기반 통합 테스트 추가
- 동일 사용자에게 동시에 여러 지출 요청을 보내는 테스트 작성
- 최종 `User.balance`와 `Transaction.balanceAfter`가 일관되는지 검증
- PostgreSQL Testcontainers 도입 검토

포트폴리오 어필 포인트:

- 단순 happy path가 아닌 장애/경합 상황 검증
- 금융성 데이터 정합성 테스트

## 4. 문서화 보강

다음 작업:

- README 작성
- API 목록 정리
- ERD 또는 테이블 설계 요약 추가
- 트러블슈팅 문서를 README에서 링크
- 실행 방법, 환경 변수, 샘플 요청 추가

포트폴리오 어필 포인트:

- 프로젝트를 처음 보는 면접관도 빠르게 이해 가능
- 문제 해결 과정과 기술 선택 이유를 명확히 전달

## 지금은 하지 않아도 되는 일

실제 은행/카드사 데이터 연동은 현 단계에서 필수로 보지 않습니다.

대신 아래 정도면 충분합니다.

- Mock 데이터 생성 API
- CSV 업로드 기능은 선택 사항
- 실제 금융 데이터는 개인정보/보안 이슈가 있으므로 샘플 데이터 기반 분석으로 대체
