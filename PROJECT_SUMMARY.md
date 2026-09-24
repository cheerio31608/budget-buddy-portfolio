# Budget Buddy Project Summary

> **초기 백엔드 단계의 기록입니다.** 현재는 세션 로그인, 반응형 웹 대시보드, DB·CSV 공통 분석, 집계 기반 Gemini 리포트까지 구현되어 일부 설명이 최신 코드와 다릅니다. 현재 상태는 [루트 README](README.md)와 [아키텍처 문서](budget-buddy/docs/ARCHITECTURE.md)를 기준으로 확인해 주세요.

Budget Buddy는 개인 가계부 데이터를 기반으로 소비 패턴을 기록하고, Gemini API를 활용해 월간 소비 리포트를 생성하는 Spring Boot 기반 백엔드 프로젝트입니다. 취업 포트폴리오 관점에서는 단순 CRUD보다 금융 데이터 정합성, 계층형 설계, 외부 API 연동, 테스트 데이터 생성 흐름을 보여주는 데 초점을 둡니다.

## 기술 스택

- Java 17
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- PostgreSQL 운영 고려, H2 개발/테스트 고려
- Google Gemini API 연동
- Gradle
- JUnit 5, Mockito

## 현재 구현 상태

### 1. 계층형 백엔드 구조

- `controller`: HTTP 요청/응답 처리
- `service`: 거래 생성, 잔액 계산, AI 리포트 생성 등 비즈니스 로직 처리
- `repository`: JPA 기반 데이터 접근
- `entity`: `User`, `Category`, `Transaction` 중심 도메인 모델
- `exception`: `BusinessException`, `ErrorCode`, `GlobalExceptionHandler` 기반 공통 예외 처리

### 2. 거래 및 잔액 처리

- 거래 생성 시 `User.balance`를 갱신합니다.
- `Transaction.balanceBefore`, `Transaction.balanceAfter`를 저장해 거래 발생 시점의 잔액 스냅샷을 남깁니다.
- 잔액 부족, 미래 거래 일자 등 기본 검증을 수행합니다.
- 동일 사용자에게 동시에 결제 요청이 들어오는 상황을 고려해 `User` row에 비관적 락을 적용했습니다.

### 3. 테스트 데이터 생성

- `DemoDataService`를 통해 거래가 없는 사용자에게 재현 가능한 데모 거래 30건을 생성할 수 있습니다.
- 분석 테스트가 가능하도록 초기 급여 거래를 먼저 생성한 뒤 시나리오 기반 지출/수입 데이터를 하나의 트랜잭션으로 저장합니다.
- 취업 포트폴리오 기준으로는 실제 금융 데이터 연동보다 이 방식이 더 안전하고 설명하기 쉽습니다.

### 4. Gemini API 기초 연동

- `GeminiClient`가 Spring `RestClient`를 사용해 Gemini API를 호출합니다.
- `AiReportService`는 최근 거래 데이터를 프롬프트로 구성해 월간 소비 분석 리포트를 생성합니다.

## 포트폴리오 관점 판단

현재 기능 수는 충분합니다. 추가로 실제 카드사/은행 데이터를 가져오는 기능은 지금 단계에서는 필수로 보지 않습니다.

이유:

- 실제 금융 데이터 연동은 인증, 약관, 개인정보 보호, 외부 API 승인 이슈가 커서 포트폴리오 본질에서 벗어날 수 있습니다.
- 채용자가 보고 싶은 것은 "외부 데이터를 많이 끌어왔는가"보다 "돈과 관련된 데이터 정합성을 어떻게 지켰는가"입니다.
- Mock 데이터 생성, AI 분석, 예외 처리, 동시성 제어만 잘 정리해도 백엔드 역량을 충분히 보여줄 수 있습니다.

따라서 다음 단계는 기능 추가보다 아래 순서를 권장합니다.

1. 동시성 제어 트러블슈팅 정리
2. QueryDSL 기반 통계 쿼리 최적화
3. AI 리포트 비동기 처리 및 DB 캐싱
4. README/API 명세/실행 방법 정리
