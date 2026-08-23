package com.gyeote.gyeote_api.domain.emergency.entity;

import com.gyeote.gyeote_api.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 긴급 호출 수신자별 상태 (DB 명세서 v1.1.0 — 3.7 alert_recipients).
 * 호출 1건 : 수신자 N명 관계를 풀어 수신 종료·만료·보관 상태를 기록한다.
 */
@Entity
@Table(
        name = "alert_recipients",
        uniqueConstraints = @UniqueConstraint(
                name = "uniq_alert_receiver",
                columnNames = {"alert_id", "receiver_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertRecipients {

    /** 수신 +24시간이 지나면 이전 알림 이력으로 이동한다. */
    public static final long EXPIRE_HOURS = 24;

    /** 만료된 수신 이력은 30일 경과 후 삭제한다. */
    public static final long RETENTION_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private EmergencyAlerts emergencyAlert;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(name = "realtime_stopped_at")
    private LocalDateTime realtimeStoppedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "delete_at", nullable = false)
    private LocalDateTime deleteAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AlertRecipients(EmergencyAlerts emergencyAlert, User receiver) {
        this.emergencyAlert = emergencyAlert;
        this.receiver = receiver;
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (expiresAt == null) {
            expiresAt = createdAt.plusHours(EXPIRE_HOURS);
        }
        if (deleteAt == null) {
            deleteAt = createdAt.plusDays(RETENTION_DAYS);
        }
    }

    public void stopRealtime(LocalDateTime stoppedAt) {
        this.realtimeStoppedAt = stoppedAt;
    }

    public void archive(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    /**
     * 수신자가 위치 수신을 종료했는지 여부.
     */
    public boolean isRealtimeStopped() {
        return realtimeStoppedAt != null;
    }
}
