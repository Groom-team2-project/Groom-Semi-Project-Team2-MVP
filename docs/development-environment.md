# 개발 환경 통합 가이드

## 목적

팀원이 동일한 MySQL 환경에서 애플리케이션 실행과 테스트를 진행할 수 있도록 Docker Compose 기반의 로컬 DB 환경을 제공합니다.

DB 접속 정보는 `.env` 기반으로 분리합니다. 실제 `.env` 파일은 커밋하지 않고, 공유용 예시는 `.env.example`만 관리합니다.

## 환경 변수 파일 생성

프로젝트 루트에서 `.env.example`을 복사해 `.env`를 생성합니다.

```bash
cp .env.example .env
```

Windows PowerShell에서는 아래 명령을 사용합니다.

```powershell
Copy-Item .env.example .env
```

로컬 개발 기본 비밀번호는 기존 개발 환경과 동일하게 `password`를 사용합니다. 운영/배포 환경에서는 `.env` 파일을 커밋하지 않고, 배포 플랫폼의 환경 변수 또는 Secret 설정으로 값을 주입합니다.

## 공통 DB 변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `MYSQL_HOST` | `localhost` | Spring 앱과 테스트가 접속할 DB 호스트 |
| `MYSQL_PORT` | `3306` | 호스트에서 노출할 MySQL 포트 |
| `MYSQL_DATABASE` | `buying_mvp` | 애플리케이션 DB |
| `MYSQL_TEST_DATABASE` | `buying_mvp_test` | MySQL 기반 테스트 DB |
| `MYSQL_USERNAME` | `root` | Spring 앱과 테스트 접속 계정 |
| `MYSQL_ROOT_PASSWORD` | `password` | 로컬 MySQL root 비밀번호 |

`SPRING_DATASOURCE_*`, `TEST_DATASOURCE_*`는 특수 환경에서만 덮어쓰는 선택 변수입니다. 기본 로컬 개발에서는 `MYSQL_*` 값만 수정하면 Docker Compose, 애플리케이션, MySQL 테스트 프로필이 같은 값을 기준으로 동작합니다.

## Docker Compose 구성

| 항목 | 기본값 |
| --- | --- |
| DB | MySQL 8.4 |
| 컨테이너 이름 | `groom-mvp-mysql` |
| 애플리케이션 DB | `buying_mvp` |
| 테스트 DB | `buying_mvp_test` |
| 포트 | `3306:3306` |
| 문자셋 | `utf8mb4` |
| 컨테이너 타임존 | `Asia/Seoul` |
| MySQL 기본 시간대 | `+09:00` |
| 네트워크 | `groom-mvp-network` |

컨테이너 타임존과 MySQL 기본 시간대는 로컬 개발환경 기준으로 고정합니다. 운영/배포 환경에서는 별도 인프라 설정에서 시간대 정책을 관리합니다.

## 실행

```bash
docker compose up -d
```

## 종료

```bash
docker compose down
```

## 데이터까지 초기화

```bash
docker compose down -v
```

`docker compose down -v`를 실행하면 MySQL 볼륨이 삭제되므로 기존 로컬 데이터도 함께 사라집니다.

## 애플리케이션 DB 연결

`src/main/resources/application.yaml`은 환경 변수를 우선 사용하고, 값이 없으면 `MYSQL_*` 로컬 기본값으로 실행됩니다.

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:buying_mvp}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8}
    username: ${SPRING_DATASOURCE_USERNAME:${MYSQL_USERNAME:root}}
    password: ${SPRING_DATASOURCE_PASSWORD:${MYSQL_ROOT_PASSWORD:password}}
```

## 테스트 DB 연결

기본 테스트 설정은 H2를 사용합니다.

```bash
./gradlew test
```

MySQL 기반으로 테스트하고 싶을 때는 `mysql` 테스트 프로필을 사용합니다.

```bash
./gradlew "-Dspring.profiles.active=mysql" test
```

Windows PowerShell에서는 아래 명령을 사용합니다.

```powershell
.\gradlew.bat "-Dspring.profiles.active=mysql" test
```

MySQL 테스트 프로필은 `src/test/resources/application-mysql.yaml`을 사용하며, 기본 테스트 DB는 `MYSQL_TEST_DATABASE` 값인 `buying_mvp_test`입니다.

## 테스트 DB 자동 생성

`docker/mysql/init/01-create-test-database.sh`에서 `MYSQL_TEST_DATABASE` 값을 기준으로 테스트 DB를 생성합니다.

이 스크립트는 MySQL 컨테이너의 데이터 볼륨이 처음 만들어질 때 실행됩니다. 이미 볼륨이 생성된 상태에서 스크립트를 다시 적용하려면 아래 명령으로 볼륨을 초기화한 뒤 다시 실행합니다.

```bash
docker compose down -v
docker compose up -d
```

## Healthcheck

Compose의 `healthcheck`는 MySQL이 실제로 접속 가능한 상태인지 확인합니다.

```yaml
test: ["CMD-SHELL", "mysqladmin ping -h localhost -uroot --password=\"$${MYSQL_ROOT_PASSWORD}\""]
```

컨테이너가 켜진 상태와 DB 접속이 가능한 상태는 다를 수 있으므로, 이 확인 절차를 통해 의존 서비스의 준비 상태를 구분합니다.
