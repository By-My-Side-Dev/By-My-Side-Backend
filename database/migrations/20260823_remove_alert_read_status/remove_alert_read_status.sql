ALTER TABLE alert_recipients
    DROP INDEX idx_receiver_read,
    DROP COLUMN is_read,
    DROP COLUMN read_at;
