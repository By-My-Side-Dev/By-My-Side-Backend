# 곁에 GYEOTE (위급 상황 실시간 위치 공유 안심 서비스)

"위급한 순간, 원터치로 내 위치를 가장 믿을 수 있는 사람에게"

---

## 목차

- [프로젝트 개요](#프로젝트-개요)
- [팀원 소개](#팀원-소개)
- [주요 기능](#주요-기능)
- [Backend](#backend)
- [시스템 아키텍처](#시스템-아키텍처)
- [Repositories](#repositories)

---

## 프로젝트 개요

폭력, 납치, 급박한 신체 위협 같은 위기 상황에서는 피해자가 직접 112에 전화를 걸거나 상황을 말로 설명하기 어렵습니다. 반대로 기존 위치 공유 앱은 평상시에도 위치를 계속 수집해 사생활 침해 우려와 배터리 소모가 큽니다. 또한 실제 위기 상황에서 나를 도와줄 수 있는 가족, 친구와의 신뢰 기반 연결망도 부족합니다.

**곁에**는 사용자가 도움 요청 버튼을 한 번 누르면 미리 연결해 둔 지인에게 긴급 알림과 현재 위치를 즉시 전달하고, 호출이 진행되는 동안 실시간 위치를 공유해 지인이 신고 등 대리 대응을 빠르게 할 수 있도록 돕는 실시간 안심 보호 서비스입니다.

### 주요 특징

- **필요할 때만 위치 공유**: 평상시에는 위치를 수집하지 않고, 긴급 호출 시에만 위치를 전송합니다.
- **원터치 긴급 전파**: 호출 즉시 연결된 지인에게 고우선순위 FCM 푸시와 주소 정보가 전달됩니다.
- **실시간 위치 추적**: STOMP WebSocket으로 발신자의 위치를 수신자에게 실시간 전달합니다.
- **신뢰 기반 지인 네트워크**: 초대와 수락으로 연결된 지인끼리만 호출과 위치를 주고받습니다.
- **요금제별 위치 엔진**: `FREE`는 최신 위치 한 건, `PAID`는 이동 경로 전체를 제공합니다.
- **개인정보 보호**: 전화번호와 디바이스 토큰을 암호화해 저장하고, 만료된 호출과 위치 데이터를 자동으로 정리합니다.

---

## 팀원 소개

| 역할 | 이름 |
| --- | --- |
| PM | (작성 필요) |
| Design | (작성 필요) |
| Frontend | (작성 필요) |
| Backend | 손세영 |

---

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| 회원가입 / 로그인 | 이메일 인증 기반 일반 회원가입, 비밀번호 재설정, JWT 기반 인증 |
| 소셜 로그인 | Kakao, Google, Apple 로그인 및 신규 회원 온보딩 |
| 지인 연결 | 전화번호 기반 사용자 검색, 지인 초대(수락/거절/취소), 지인 관리, 사용자 차단 |
| 긴급 호출 | 원터치 호출 생성 및 종료, 최초 위치와 역지오코딩 주소 전달 |
| 실시간 위치 공유 | 호출 중 발신자 위치를 WebSocket으로 실시간 전송, 사용자별 공유 ON/OFF 설정 |
| 수신 알림 | 받은 긴급 알림 목록, 상세 조회, 알림 이력 |
| 위치 지도 | 수신한 호출의 지도 마커와 이동 경로(`PAID`) 조회 |
| 푸시 알림 | FCM 푸시 발송, 실패 시 재시도 |
| 마이페이지 | 프로필 조회/수정, 프로필 이미지 업로드, 회원 탈퇴 |

### 긴급 호출 흐름

1. 발신자가 긴급 호출을 생성하면 최초 위치가 저장되고, 연결된 지인에게 FCM 푸시가 발송됩니다.
2. 수신자는 `/topic/emergency-alerts/{alertId}/location`을 구독해 위치를 실시간으로 받습니다.
3. 발신자는 실시간 위치 공유 설정이 켜진 동안 일정 간격(기본 15초)으로 위치를 전송합니다.
   - `PAID` 플랜: 위치 이력을 누적 저장해 이동 경로를 제공합니다.
   - `FREE` 플랜: 최신 위치 한 건만 유지(덮어쓰기)합니다.
4. 호출은 발신자가 직접 종료하거나 생성 후 24시간이 지나면 만료되며, 스케줄러가 관련 데이터를 정리합니다.

---

## Backend

### 기술 스택

| 항목 | 사용 기술 / 라이브러리 |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 4.1, Spring Web MVC |
| 실시간 통신 | Spring WebSocket (STOMP) |
| Security | Spring Security, JWT (jjwt), OAuth2/OIDC 토큰 검증 |
| Database | MySQL 8.4, Spring Data JPA, Flyway |
| Cache / Session | Redis 7, Spring Session Data Redis |
| 외부 연동 | Firebase Cloud Messaging, Azure Blob Storage, Kakao 로컬 API, SMTP 메일 |
| API 문서 | Springdoc OpenAPI (Swagger UI), AsyncAPI 3.1 |
| 모니터링 | Spring Boot Actuator, Micrometer Prometheus |
| 인프라 | Docker, GitHub Actions, Azure Container Registry, Azure VM, Nginx |

### 백엔드 프로젝트 구조

```text
src/main/java/com/gyeote/gyeote_api
├── domain/                 # 비즈니스 도메인
│   ├── auth/               # 회원가입, 로그인, 소셜 로그인, 이메일 인증
│   ├── user/               # 프로필, 실시간 위치 공유 설정, 탈퇴
│   ├── contact/            # 지인 초대, 지인 관리, 차단
│   ├── emergency/          # 긴급 호출, 위치 갱신, 역지오코딩
│   ├── alert/              # 수신 긴급 알림
│   ├── location/           # 지도 마커, 이동 경로
│   ├── device/             # FCM 디바이스 토큰
│   └── notification/       # FCM 발송 및 재시도
└── global/                 # 공통 인프라
    ├── auth/
    ├── config/
    ├── exception/
    ├── response/
    ├── security/           # JWT, 암호화, WebSocket 인증
    ├── storage/            # Azure Blob Storage, 파일 검증
    └── websocket/

src/main/resources
├── application.yaml        # 공통 설정
├── application-local.yaml  # 로컬 설정
├── application-prod.yaml   # 운영 설정
└── db/migration/           # Flyway 마이그레이션 SQL

database/migrations/        # 마이그레이션 운영 문서
docker/                     # 로컬 / Blue-Green Compose, Nginx 설정
script/                     # 배포, AsyncAPI 문서 생성 스크립트
docs/                       # AsyncAPI 명세, 작업 문서
```

### API 문서

- REST API: 애플리케이션 실행 후 `/swagger-ui/index.html`
- STOMP WebSocket API UI: 애플리케이션 실행 후 `/asyncapi/`
- AsyncAPI 원본: [`docs/asyncapi.yaml`](docs/asyncapi.yaml)
- 헬스 체크: `/health`

AsyncAPI HTML 문서를 로컬에서 다시 생성하려면 Node.js가 설치된 환경에서 실행합니다.

```bash
./script/generate-asyncapi-docs.sh
```

### 로컬 실행

1. JDK 17, Docker를 준비합니다.
2. `.env.local`에 아래 환경 변수를 설정합니다. 비밀 정보이므로 **절대 커밋하지 않습니다.**

   | 구분 | 환경 변수 |
   | --- | --- |
   | DB | `SPRING_DATASOURCE_URL`, `DB_USERNAME`, `DB_PASSWORD` |
   | Redis | `REDIS_IP`, `REDIS_PORT` |
   | JWT | `JWT_SECRET` |
   | 개인정보 암호화 | `PHONE_ENC_KEY`, `PHONE_HMAC_SECRET`, `PHONE_PEPPER`, `FCM_TOKEN_ENC_KEY` |
   | 소셜 로그인 | `GOOGLE_OAUTH_CLIENT_IDS`, `APPLE_OAUTH_CLIENT_IDS` |
   | Kakao | `KAKAO_REST_API_KEY` |
   | Firebase | `FIREBASE_PROJECT_ID` |
   | Azure Storage | `AZURE_STORAGE_ACCOUNT_NAME`, `AZURE_STORAGE_CONTAINER_NAME` |
   | WebSocket | `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` |
   | 메일 | `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` |

3. MySQL과 Redis를 실행합니다.

   ```bash
   docker compose -f docker/docker-compose.local.yml --env-file .env.local up -d
   ```

4. 애플리케이션을 실행합니다. 기본 프로필은 `local`입니다.

   ```bash
   ./gradlew bootRun
   ```

5. 테스트를 실행합니다.

   ```bash
   ./gradlew test
   ```

> `local` 프로필은 Hibernate `ddl-auto: create`로 실행할 때마다 스키마를 다시 생성하며 Flyway를 실행하지 않습니다.
> `prod` 프로필은 Flyway로 스키마를 관리하고 Hibernate는 `validate`만 수행합니다.

### 배포

- 배포 환경: Azure VM (Docker 컨테이너)
- 이미지 저장소: Azure Container Registry
- 배포 방식: `main` 브랜치에 push되면 GitHub Actions가 빌드, 이미지 push, Blue-Green 무중단 배포를 수행합니다.
  1. AsyncAPI 문서 생성 및 애플리케이션 빌드
  2. Docker 이미지 빌드 후 ACR에 push
  3. Azure VM에서 `script/deploy.sh` 실행
     - 현재 활성 색상(blue/green)의 반대 색상 컨테이너를 띄웁니다.
     - `/health` 헬스 체크에 성공하면 Nginx upstream을 전환하고 기존 컨테이너를 종료합니다.
     - 헬스 체크에 실패하면 새 컨테이너를 내리고 기존 버전을 유지합니다.

### 기여 규칙

- 작업 전 GitHub 이슈를 만들고 `develop`에서 `feature/#{이슈번호}-{작업명}` 형식으로 브랜치를 생성합니다.
- 커밋 메시지: `{type}: {한국어 요약} (#{이슈번호})`
- PR은 `develop`을 대상으로 하며 `.github/PULL_REQUEST_TEMPLATE.md`를 따릅니다.
- 자세한 규칙은 [`AGENTS.md`](AGENTS.md)를 참고합니다.

---

## 시스템 아키텍처

```text
┌──────────────────────────┐
│   Mobile App              │  Android / iOS
└────────────┬─────────────┘
             │  REST (HTTPS) / STOMP over WebSocket
┌────────────▼─────────────┐
│   Nginx                   │  리버스 프록시, Blue-Green 전환
└────────────┬─────────────┘
             │
┌────────────▼─────────────┐
│   Spring Boot API         │  Azure VM (Docker)
└──────┬─────────────┬─────┘
       │             │
┌──────▼──────┐ ┌────▼──────┐
│   MySQL      │ │   Redis    │
└─────────────┘ └───────────┘
             │
┌────────────▼──────────────────────────────┐
│   External Services                         │
│  FCM / Azure Blob Storage / Kakao 로컬 API  │
│  Kakao·Google·Apple 로그인 / SMTP 메일      │
└────────────────────────────────────────────┘
```

---

## Repositories

| Repo | 설명 |
| --- | --- |
| [gyeote-backend](https://github.com/GYEOTE-project/gyeote-backend) | 백엔드 API 서버 (Spring Boot) |
| (작성 필요) | 모바일 앱 클라이언트 |

---

곁에 GYEOTE © 2026
