-- Requires MySQL 8.0.1+ because workers use SELECT ... FOR UPDATE SKIP LOCKED.
-- Apply this migration before deploying an application configured with ddl-auto=validate.

CREATE TABLE device_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    token_encrypted VARBINARY(5000) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    platform VARCHAR(10) NOT NULL,
    app_version VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uniq_device_token_user UNIQUE (user_id),
    CONSTRAINT uniq_device_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_device_token_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_device_token_platform CHECK (platform IN ('ANDROID', 'IOS'))
) ENGINE=InnoDB;

CREATE TABLE fcm_delivery_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    alert_id BIGINT NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    processing_started_at DATETIME(6) NULL,
    last_error_code VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uniq_fcm_delivery_alert_recipient UNIQUE (alert_id, recipient_user_id),
    CONSTRAINT fk_fcm_delivery_alert FOREIGN KEY (alert_id) REFERENCES emergency_alerts (id),
    CONSTRAINT fk_fcm_delivery_recipient FOREIGN KEY (recipient_user_id) REFERENCES users (id),
    CONSTRAINT chk_fcm_delivery_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'SUCCEEDED', 'RETRYABLE_FAILED', 'PERMANENT_FAILED',
        'EXPIRED', 'NO_REGISTRATION'
    )),
    INDEX idx_fcm_delivery_claim (status, next_attempt_at, processing_started_at),
    INDEX idx_fcm_delivery_created_at (created_at)
) ENGINE=InnoDB;
