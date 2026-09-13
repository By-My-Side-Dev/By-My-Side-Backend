package com.gyeote.gyeote_api.domain.notification.entity;

import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "fcm_delivery_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uniq_fcm_delivery_alert_recipient",
                columnNames = {"alert_id", "recipient_user_id"}
        )
)
public class FcmDeliveryLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private EmergencyAlerts alert;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public FcmDeliveryLog(EmergencyAlerts alert, User recipient) {
        this.alert = alert;
        this.recipient = recipient;
        this.status = DeliveryStatus.PENDING;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (nextAttemptAt == null) nextAttemptAt = now;
    }

    public void claim(LocalDateTime now) {
        status = DeliveryStatus.PROCESSING;
        processingStartedAt = now;
        attemptCount++;
        updatedAt = now;
    }

    public void finish(DeliveryStatus finalStatus, String errorCode, LocalDateTime now) {
        status = finalStatus;
        lastErrorCode = errorCode;
        processingStartedAt = null;
        updatedAt = now;
    }

    public void retry(String errorCode, LocalDateTime nextAttemptAt, LocalDateTime now) {
        status = DeliveryStatus.RETRYABLE_FAILED;
        lastErrorCode = errorCode;
        processingStartedAt = null;
        this.nextAttemptAt = nextAttemptAt;
        updatedAt = now;
    }

}
