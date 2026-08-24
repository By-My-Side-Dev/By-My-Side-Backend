-- 신규 사용자는 실시간 위치 공유를 기본으로 허용한다.
-- 기존 false 값은 사용자가 직접 끈 설정일 수 있으므로 변경하지 않는다.
ALTER TABLE users
    ALTER COLUMN realtime_location_enabled SET DEFAULT TRUE;
