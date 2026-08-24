-- 실시간 위치 공유 여부는 users.realtime_location_enabled를 단일 기준으로 사용한다.
ALTER TABLE emergency_alerts
    DROP COLUMN is_realtime;
