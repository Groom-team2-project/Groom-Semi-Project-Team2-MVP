# 운영 배포 가이드

`develop` 브랜치 CI는 백엔드와 frontend-v2 이미지를 빌드한 뒤, 커밋 SHA 태그와 `latest` 태그를 GHCR에 올립니다. 스테이징 서버와 Secret 설정이 완료되기 전까지 실제 배포는 GitHub Actions에서 수동으로 실행합니다.

## 1. 서버 준비

Linux 서버에 Docker Engine과 Docker Compose 플러그인을 설치합니다. 이후 배포 디렉터리와 운영 환경 변수 파일을 준비합니다.

```bash
sudo mkdir -p /opt/soldout/deploy
sudo chown -R "$USER":"$USER" /opt/soldout
cp deploy/.env.example /opt/soldout/.env
chmod 600 /opt/soldout/.env
```

`.env`의 `replace-with-...` 값을 모두 실제 운영 값으로 교체합니다. 카카오 Redirect URI는 `https://soldout.example.com/oauth/kakao/callback`처럼 외부에서 접속할 프론트엔드 주소를 사용해야 합니다.

Compose는 `MYSQL_USERNAME`에 지정한 애플리케이션 전용 계정을 생성합니다. MySQL 초기화 환경 변수는 데이터 볼륨이 처음 생성될 때만 적용되므로, 기존 볼륨을 재사용한다면 해당 계정과 권한을 직접 생성해야 합니다.

`OAUTH_COOKIE_SECURE`의 기본값은 `true`이므로 OAuth 로그인에는 HTTPS가 필요합니다. 임시 HTTP 스테이징에서만 `false`로 설정할 수 있으며, 외부 배포 전에는 반드시 `true`로 되돌립니다.

GHCR 패키지가 비공개라면 서버에서 `read:packages` 권한을 가진 GitHub classic PAT로 한 번 로그인합니다.

```bash
echo "$GHCR_READ_TOKEN" | docker login ghcr.io -u GITHUB_USERNAME --password-stdin
```

해당 토큰은 저장소나 애플리케이션 `.env`에 기록하지 않습니다.

## 2. GitHub 설정

Repository Settings에서 `staging` Environment를 생성하고 다음 Environment Secret을 등록합니다.

| Secret | 예시 또는 설명 |
| --- | --- |
| `DEPLOY_HOST` | 서버 공인 IP 또는 도메인 |
| `DEPLOY_PORT` | `22` |
| `DEPLOY_USER` | `ubuntu` |
| `DEPLOY_SSH_KEY` | 서버 접속용 SSH 개인 키 전문 |
| `DEPLOY_HOST_FINGERPRINT` | 서버 SSH 호스트 키의 SHA256 fingerprint |
| `DEPLOY_PATH` | `/opt/soldout` |

JWT, DB, 카카오, 토스 등의 애플리케이션 Secret은 서버의 `/opt/soldout/.env`에서 관리하므로 GitHub Actions에 중복 등록할 필요가 없습니다.

## 3. 배포 실행

GitHub Actions에서 `CD` 워크플로를 선택하고 **Run workflow**를 실행합니다. 가장 최근의 정상 `develop` 이미지는 `latest`를 사용하고, 특정 버전을 배포하려면 해당 커밋 SHA 태그를 입력합니다.

워크플로는 Compose와 배포 스크립트만 서버로 전송하며 서버의 `.env`를 덮어쓰지 않습니다. 이후 지정한 백엔드와 프론트엔드 이미지를 내려받고, MySQL과 Redis를 내부 네트워크에서 시작한 뒤 프론트엔드와 백엔드 응답을 확인합니다.

## 4. 롤백

`CD` 워크플로를 다시 실행하면서 마지막 정상 버전의 커밋 SHA를 이미지 태그로 입력합니다. CI가 백엔드와 frontend-v2에 동일한 SHA 태그를 발급하므로 두 이미지를 같은 버전으로 되돌릴 수 있습니다.

## 네트워크 정책

`docker-compose.prod.yml`은 프론트엔드 포트만 외부에 공개합니다. MySQL과 Redis는 Docker 내부 네트워크에서만 접근할 수 있으며, 백엔드는 서버의 loopback 주소에만 연결됩니다. 프론트엔드 Nginx가 `/api` 요청을 내부 백엔드로 전달합니다.
