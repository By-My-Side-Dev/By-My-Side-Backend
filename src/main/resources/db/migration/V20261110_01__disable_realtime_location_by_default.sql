-- 신규 계정은 사용자가 직접 활성화하기 전까지 실시간 위치를 공유하지 않는다.
-- 컬럼 기본값만 변경하며 기존 사용자의 true/false 설정은 유지한다.
ALTER TABLE users
    ALTER COLUMN realtime_location_enabled SET DEFAULT FALSE;
