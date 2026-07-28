# 이벤트 락 성능 비교

선착순 이벤트 참여 API의 Redisson 분산락과 MySQL 비관적 쓰기 락을 동일한 조건에서 비교한다.
운영 기본 전략은 기존과 동일한 `distributed`이며, 비교용 API는 `performance` 프로필에서만 생성된다.

## 실행

Docker Desktop을 실행한 뒤 프로젝트 루트에서 다음 명령을 사용한다.

```powershell
.\performance\run-lock-comparison.ps1
```

부하를 바꾸려면 다음처럼 실행한다.

```powershell
.\performance\run-lock-comparison.ps1 -Vus 100 -Iterations 500 -Runs 3
```

실행 순서 편향을 확인하려면 전략 순서를 뒤집을 수 있다.

```powershell
.\performance\run-lock-comparison.ps1 `
  -StrategyOrder @('pessimistic', 'distributed')
```

스크립트는 MySQL과 Redis를 시작하고, 애플리케이션을 전략별로 재기동한 뒤 워밍업과 본 측정을
실행한다. 결과는 `docs/event-lock-performance-report.md`에 기록된다. k6 원본 요약과 애플리케이션
로그는 `performance/results`에 남지만 Git에는 포함하지 않는다.

## 측정 원칙

- 두 전략은 같은 HTTP API, 같은 DB 저장 로직, 같은 이벤트 정원과 요청 수를 사용한다.
- 회원 ID는 요청마다 다르게 생성하여 중복 참여 거절이 측정값에 섞이지 않게 한다.
- 이벤트 정원은 요청 수와 같아 정상 조건에서는 모든 요청이 성공할 수 있다.
- 처리량과 응답 시간뿐 아니라 이벤트 참여 수와 참여 테이블 행 수가 일치하는지도 확인한다.
- 카카오 인증과 외부 결제 호출은 락 비교에서 제외한다.

## 주의

이 측정은 로컬 단일 호스트의 엔드투엔드 비교다. 여러 애플리케이션 인스턴스를 조정하는
분산락의 운영상 장점이나 실제 네트워크 지연을 완전히 재현하지 않는다.
