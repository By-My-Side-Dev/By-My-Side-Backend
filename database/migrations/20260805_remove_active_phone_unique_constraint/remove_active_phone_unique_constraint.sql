-- 기존 DB에 남아 있는 전화번호 중복 방지용 generated column과 UNIQUE 인덱스를 제거한다.
-- 신규 DB에는 active_phone_hash 컬럼이 생성되지 않으므로 실행할 필요가 없다.
ALTER TABLE user_phones DROP COLUMN active_phone_hash;
