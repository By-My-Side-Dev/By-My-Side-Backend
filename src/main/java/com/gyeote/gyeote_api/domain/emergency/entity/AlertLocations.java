package com.gyeote.gyeote_api.domain.emergency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 실시간 위치 공유가 활성화된 긴급 호출의 위치 이력.
 * 위치를 수신할 때마다 새 행을 생성하여 이동 경로를 누적한다.
 */
@Entity
@Table(
        name = "alert_locations",
        indexes = @Index(
                name = "idx_alert_updated_at",
                columnList = "alert_id, updated_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertLocations {

    public static final long RETENTION_DAYS = 14;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private EmergencyAlerts emergencyAlert;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "delete_at", nullable = false)
    private LocalDateTime deleteAt;

    @Builder
    private AlertLocations(
            EmergencyAlerts emergencyAlert,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.emergencyAlert = emergencyAlert;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @PrePersist
    private void prePersist() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (deleteAt == null) {
            deleteAt = updatedAt.plusDays(RETENTION_DAYS);
        }
    }
}
