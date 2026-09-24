-- 기존 사용자는 무료 플랜으로 이전하고 이후 위치 저장 방식을 사용자 플랜으로 구분한다.
ALTER TABLE users
    ADD COLUMN plan VARCHAR(20) NOT NULL DEFAULT 'FREE' AFTER realtime_location_enabled;
