# 신규 계정 실시간 위치 공유 기본값 OFF

관련 이슈: #117

## 적용 범위
- SQL: `src/main/resources/db/migration/V20261110_01__disable_realtime_location_by_default.sql`
- `users.realtime_location_enabled`의 기본값만 FALSE로 변경한다.
- 기존 행의 true/false와 version은 갱신하지 않는다. 기존 Flyway 파일은 수정하지 않는다.
- 일반·소셜 신규 가입은 User 엔티티 기본값 false를 저장한다. 기존 계정 소셜 연결은 설정을 유지한다.

## 배포 및 확인
- 애플리케이션 배포 시 Flyway가 신규 마이그레이션을 실행한다. ALTER TABLE의 메타데이터 잠금 대기는 운영 트래픽에 따라 발생할 수 있다.
- `SHOW COLUMNS FROM users LIKE 'realtime_location_enabled';`로 기본값 0을 확인한다.
- 신규 일반·소셜 가입 후 프로필의 realtimeLocationEnabled=false를 확인한다.
- 기존 설정 true/false 계정의 값이 유지되고 설정 API로 ON/OFF가 가능한지 확인한다.
- 최초 도움 요청 위치는 제공되며 OFF 상태의 후속 REST·STOMP 위치 갱신은 거절되는지 확인한다.

## 복구
- 배포된 마이그레이션은 수정하거나 삭제하지 않는다. 필요하면 새 Flyway로 기본값을 TRUE로 되돌리고 애플리케이션 기본값도 함께 복구한다.
- 이 변경 이후 생성된 계정의 false는 자동으로 true로 변환하지 않는다.

## 로컬 SQL 검증 결과
- 2026-11-10, Docker MySQL 8.4에서 세션 전용 임시 users 테이블에 실제 SQL 파일을 실행해 PASS를 확인했다.
- 기존 true/false 및 version 값은 유지됐고, 컬럼값을 생략한 신규 INSERT는 false를 저장했다.
- 임시 테이블은 제거했으며 실제 사용자 테이블은 변경하지 않았다. 이 검증은 SQL 동작 검증이며 전체 Flyway 기동 검증은 포함하지 않는다.
- 추가로 별도 검증 스키마에서 직전 버전 20261024.01을 baseline으로 prod 프로필 JAR를 기동했다. 신규 Flyway 20261110.01 적용 성공, Hibernate validate 및 `/health` UP을 확인했다.
- 검증 스키마 실제 테이블의 기존 ON/OFF·version 보존 및 기본값 생략 신규 INSERT OFF를 확인했다. HTTP 프로필 OFF 및 설정 ON/OFF도 PASS다. 운영 DB에는 적용하지 않았다.
