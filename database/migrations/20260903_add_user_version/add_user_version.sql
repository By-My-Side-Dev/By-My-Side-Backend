-- 사용자 정보 동시 수정 시 낙관적 잠금 충돌을 감지하기 위한 버전 컬럼을 추가한다.
ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
