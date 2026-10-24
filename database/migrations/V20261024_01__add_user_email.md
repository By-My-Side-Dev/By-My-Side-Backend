# 회원가입 인증용 이메일 컬럼 도입

관련 이슈: #91

## 스키마 및 적용

- Flyway SQL: `src/main/resources/db/migration/V20261024_01__add_user_email.sql`
- `users.email VARCHAR(255) NULL`, `uniq_email UNIQUE(email)` 추가
- 기존 사용자와 소셜 가입 계정은 NULL을 유지한다. 임의 이메일로 채우지 않는다.
- 이메일은 요청 DTO에서 앞뒤 공백 제거 및 소문자로 정규화한다.
- 기존 loginId와 지인 API는 유지한다.
- 기존 데이터에 이메일을 연결하는 기능은 이번 범위에 없다. 이메일이 없는 기존 계정은 신규 재설정 기능을 사용할 수 없다.

## 배포 설정

운영 환경에 다음 환경변수를 설정한다. 실제 값은 이 문서나 Git에 저장하지 않는다.

| 변수 | 용도 |
|---|---|
| `MAIL_ENABLED` | 운영 기본 true, 로컬 기본 false |
| `MAIL_HOST` | SMTP 서버 주소 |
| `MAIL_PORT` | SMTP STARTTLS 포트, 기본 587 |
| `MAIL_USERNAME` | SMTP 인증 계정 |
| `MAIL_PASSWORD` | SMTP 인증 비밀번호 |
| `MAIL_FROM` | 발신 이메일 주소 |

- SMTP는 인증과 STARTTLS를 사용하고 연결·읽기·쓰기 타임아웃은 각각 5초다.
- `mail.enabled=false`는 No-op 전송으로 이메일·인증번호·토큰을 로그에 기록하지 않는다. 실제 이메일 검증은 SMTP 설정 후 확인한다.
- 발송은 전용 비동기 실행기에서 처리한다. 200 응답은 메일함 도착을 보장하지 않는다. 발송 실패는 개인정보 없는 오류 메타데이터만 기록한다.
- SMTP는 배포 `/health` 판정에서 제외한다. 메일 전송 오류는 별도로 관찰해야 한다.
- 비밀 키·환경파일 원문은 조회하거나 수정하지 않는다. 담당자가 배포 환경에 직접 주입한다.

## API 연동

경로 접두사: `/api/v1/auth`. 모두 비로그인 접근을 허용하며 응답은 기존 `ApiResponse` 형식을 따른다.

| 경로 (POST) | 요청 | 성공 응답 data |
|---|---|---|
| `/email/signup/code` | email | 없음 |
| `/email/signup/verify` | email, code | emailVerificationToken |
| `/signup` | 기존 가입 필드 + email, emailVerificationToken | 없음 |
| `/password-reset/code` | loginId, email | 없음 |
| `/password-reset/verify` | loginId, email, code | resetToken |
| `/password-reset/confirm` | resetToken, newPassword | 없음 |

- 가입 이메일 중복: 409 `SIGNUP_DUPLICATED_EMAIL`
- 가입 인증 토큰 만료·이메일 불일치·재사용: 400 `SIGNUP_EMAIL_NOT_VERIFIED`
- 인증번호 불일치·만료 및 재설정 계정 미일치: 400 `AUTH_INVALID_EMAIL_CODE`
- 재요청 제한: 429 `AUTH_EMAIL_COOLDOWN`, 일일 제한: 429 `AUTH_EMAIL_DAILY_LIMIT`
- 재설정 토큰 만료·재사용: 400 `AUTH_INVALID_RESET_TOKEN`
- 가입 전송 작업 큐 포화: 503 `AUTH_EMAIL_DELIVERY_UNAVAILABLE`
- 잘못된 필드 형식은 기존 요청 검증 오류로 반환한다.

## 제한 및 복구 동작

- 코드 유효기간 5분, 실패 최대 5회, 재요청 간격 60초.
- 일일 요청 제한은 한국 시간 날짜 기준 이메일당 10회이며 SIGNUP/PASSWORD_RESET이 공유한다. 미일치 재설정 요청도 같은 카운터와 쿨다운을 사용한다.
- 새 코드 요청 시 이전 코드 및 실패 횟수를 초기화한다.
- 검증 성공 시 코드 삭제와 10분 유효 UUID 토큰 발급을 하나의 Lua 연산으로 수행한다.
- 가입·비밀번호 변경 토큰은 GETDEL로 한 번만 소모된다. 이후 DB 작업 실패 시 토큰을 복구하지 않으므로 재인증이 필요하다.
- 비밀번호 재설정은 활성 일반 로그인 계정만 가능하다. 기존 Refresh Token을 삭제하며 기존 Access Token은 만료 시까지 유효하다.
- 비밀번호 변경·토큰 발급·재발급은 사용자 행 잠금을 공유한다. 비밀번호 변경 중인 요청과 Redis 세션 저장이 교차하지 않도록 순서를 보장한다. Redis 지연 시 같은 사용자의 인증 요청은 DB 행 잠금을 기다릴 수 있다.
- Redis와 DB는 분산 트랜잭션이 아니다. DB 롤백이 Redis 토큰을 복구하지 않으며, DB 최종 커밋 실패 시 이미 삭제한 세션은 재로그인이 필요하다.

## 배포 후 확인

- Flyway 적용과 앱 기동 및 `/health` 정상 응답 확인
- 실제 SMTP 발송과 6자리 코드 검증, 가입 및 중복 이메일 409 확인
- 재요청 제한·오입력 5회 제한·토큰 재사용 거부 확인
- 활성 계정 비밀번호 변경 후 이전 비밀번호 로그인 및 기존 Refresh Token 재발급 거부 확인
- 계정 미일치 요청도 같은 성공 응답·제한 정책인지 확인

DB 컬럼 삭제는 사용자 이메일을 잃게 하므로 자동 롤백 SQL을 제공하지 않는다.
