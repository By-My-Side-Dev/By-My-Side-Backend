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
 * 긴급 호출의 위치.
 * 실시간 호출은 위치를 수신할 때마다 새 행을 생성하고,
 * 1회성 호출은 최초에 생성한 행의 좌표를 계속 갱신한다.
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

    public void updateCoordinates(
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime updatedAt
    ) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = updatedAt;
        this.deleteAt = updatedAt.plusDays(RETENTION_DAYS);
    }
}
