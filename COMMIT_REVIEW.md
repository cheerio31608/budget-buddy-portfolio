# Troubleshooting Report: Concurrent Balance Update

## 배경

Budget Buddy는 거래가 생성될 때 `User.balance`를 갱신하고, `Transaction.balanceBefore`, `Transaction.balanceAfter`에 거래 시점의 잔액 스냅샷을 저장합니다. 이 구조에서는 여러 결제 요청이 동시에 들어올 때 잔액 정합성이 깨지지 않는 것이 중요합니다.

테스트 데이터 생성과 거래 생성 API를 점검하던 중, 동일 사용자의 결제 요청이 짧은 시간에 여러 개 발생하는 상황을 가정하면 기존 구조에 동시성 문제가 생길 수 있음을 확인했습니다.

## 문제 상황

기존 `TransactionService.createTransaction()`은 다음 흐름으로 동작했습니다.

1. `userRepository.findById(userId)`로 사용자 조회
2. 현재 잔액 확인
3. `balanceAfter` 계산
4. 거래 저장
5. `user.updateBalance()`로 사용자 잔액 변경

단일 요청에서는 정상 동작하지만, 동시에 두 요청이 들어오면 두 트랜잭션이 같은 `User.balance`를 읽을 수 있습니다.

예시:

- 초기 잔액: 10,000원
- A 요청: 7,000원 지출
- B 요청: 5,000원 지출

락이 없으면 A와 B가 모두 10,000원을 기준으로 잔액 검증을 통과할 수 있습니다. 이 경우 실제로는 총 12,000원 지출이므로 하나는 실패해야 하지만, 둘 다 성공하거나 `balanceAfter` 스냅샷이 실제 최종 잔액과 어긋날 수 있습니다.

## 원인

원인은 잔액을 가진 `users` row를 읽는 시점에 동시 접근 제어가 없었다는 점입니다.

`@Transactional`은 하나의 요청 내부 작업을 원자적으로 묶어주지만, 여러 트랜잭션이 같은 row를 동시에 읽고 계산하는 상황까지 자동으로 직렬화하지는 않습니다. 따라서 잔액처럼 경합이 발생하는 데이터에는 명시적인 잠금 전략이 필요합니다.

## 해결 방법

사용자 잔액을 변경하는 거래 생성 흐름에 비관적 락을 적용했습니다.

### 변경 전

```java
User user = userRepository.findById(request.getUserId())
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
```

### 변경 후

```java
User user = userRepository.findByIdForUpdate(request.getUserId())
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
```

`UserRepository`에는 다음 메서드를 추가했습니다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select u from User u where u.userId = :userId")
Optional<User> findByIdForUpdate(@Param("userId") Long userId);
```

JPA의 `PESSIMISTIC_WRITE`는 PostgreSQL에서 일반적으로 `SELECT ... FOR UPDATE` 성격의 row lock으로 동작합니다. 같은 사용자의 잔액을 변경하려는 두 번째 트랜잭션은 첫 번째 트랜잭션이 끝날 때까지 대기합니다.

## 추가 개선

거래 저장 흐름도 더 명확하게 정리했습니다.

변경 전에는 `balanceAfter`를 별도로 계산한 뒤 거래를 저장하고, 마지막에 `user.updateBalance()`를 호출했습니다.

변경 후에는 다음 순서로 정리했습니다.

1. 잠금 조회로 사용자 row 획득
2. 잔액 부족 여부 검증
3. `balanceBefore` 저장
4. `user.updateBalance()`로 실제 잔액 변경
5. 변경된 `user.getBalance()`를 `Transaction.balanceAfter`로 저장

이렇게 하면 거래 스냅샷이 실제 엔티티 상태를 기준으로 저장되므로 코드 흐름이 더 직관적입니다.

## 변경 파일

- `UserRepository.java`
  - `findByIdForUpdate()` 추가
  - `@Lock(LockModeType.PESSIMISTIC_WRITE)` 적용

- `TransactionService.java`
  - 사용자 조회를 `findById()`에서 `findByIdForUpdate()`로 변경
  - 잔액 변경 후 실제 변경된 값을 `balanceAfter`에 저장하도록 흐름 정리

- `TransactionServiceTest.java`
  - Mock expectation을 `findByIdForUpdate()` 기준으로 수정

## 기대 효과

- 동일 사용자에 대한 동시 결제 요청을 순차 처리
- 잔액 부족 검증의 신뢰성 향상
- `User.balance`와 `Transaction.balanceAfter` 간 정합성 강화
- 금융 도메인에서 중요한 데이터 무결성 이슈를 포트폴리오에 명확히 설명 가능

## 향후 보완

현재는 서비스 단위 테스트를 기존 구조에 맞게 갱신했습니다. 동시성 제어의 효과를 더 강하게 증명하려면 PostgreSQL 기반 통합 테스트를 추가하는 것이 좋습니다.

추천 테스트:

- 동일 사용자에게 여러 지출 요청을 동시에 발생
- 최종 잔액이 기대값과 일치하는지 검증
- 잔액을 초과하는 동시 지출 중 일부가 실패하는지 검증
- 각 거래의 `balanceBefore`, `balanceAfter`가 순차적으로 이어지는지 검증

분산 서버 환경에서는 Redis/Redisson 기반 분산 락을 검토할 수 있습니다. 다만 현재 단계에서는 DB row lock이 단순하고 신뢰성이 높으므로 우선 적용 전략으로 적합합니다.
