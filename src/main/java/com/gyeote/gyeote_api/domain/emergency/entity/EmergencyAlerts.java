package com.gyeote.gyeote_api.domain.emergency.entity;

import com.gyeote.gyeote_api.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "emergency_alerts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmergencyAlerts {

    /** 긴급 호출 생성 후 24시간이 지나면 자동으로 종료된다. */
    public static final long EXPIRE_HOURS = 24;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "address")
    private String address;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder
    private EmergencyAlerts(
            User sender,
            BigDecimal latitude,
            BigDecimal longitude,
            String address
    ) {
        this.sender = sender;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
    }

    @PrePersist
    private void prePersistCreateAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void end(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public boolean isEnded(LocalDateTime now) {
        return resolveEndedAt(now) != null;
    }

    public LocalDateTime resolveEndedAt(LocalDateTime now) {
        if (endedAt != null) {
            return endedAt;
        }
        if (createdAt != null && !createdAt.plusHours(EXPIRE_HOURS).isAfter(now)) {
            return createdAt.plusHours(EXPIRE_HOURS);
        }
        return null;
    }

    public void updateLocation(
            BigDecimal latitude,
            BigDecimal longitude,
            String address
    ) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
    }
}
