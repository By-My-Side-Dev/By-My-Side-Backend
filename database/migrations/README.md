# Flyway 마이그레이션

운영(`prod`)에서는 애플리케이션 시작 시 Flyway를 실행한 뒤 Hibernate가 스키마를 검증한다. SQL은 `flyway/`에서 관리하며 Gradle과 Docker 빌드에서 JAR의 `db/migration`에 포함한다. 로컬(`local`)은 기존대로 Hibernate `create`를 사용하고 Flyway를 실행하지 않는다.

## 기존 운영 DB 최초 적용

- 현재 baseline은 `20260910.01`이다. 기존 DB에 이 버전까지의 SQL 변경이 모두 반영됐는지 배포 전에 확인한다. baseline은 기존 스키마의 일치 여부를 검증하거나 누락된 SQL을 실행하지 않는다.
- history가 없는 기존 스키마는 `baseline-on-migrate: true`로 기준 버전을 기록한다. 이 버전 이하의 SQL은 실행하지 않고 이후 버전부터 실행한다.
- 최초 배포 후 기동 로그와 다음 조회로 baseline 및 이후 마이그레이션의 성공 여부를 확인한다.

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

- 최초 적용 확인 후 운영 설정의 `baseline-on-migrate`를 `false`로 변경한다. 잘못 지정된 기존 DB를 자동으로 baseline 처리하는 것을 방지한다.
- 현재 SQL은 기존 스키마의 변경 이력이다. 빈 DB를 초기화하는 전체 스키마 생성 SQL은 포함하지 않는다.

## 이후 변경

`flyway/VYYYYMMDD_NN__description.sql` 형식으로 baseline보다 높은 버전의 새 파일을 추가한다. 적용된 SQL은 수정하거나 삭제하지 않는다. 운영은 `ddl-auto: validate`이므로 Entity 변경에 필요한 DDL도 함께 추가한다.
