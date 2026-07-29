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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "latitude", nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false)
    private BigDecimal longitude;

    @Column(name = "address")
    private String address;

    @Column(name = "is_realtime", nullable = false)
    private boolean isRealtime;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private EmergencyAlerts(User sender, BigDecimal latitude, BigDecimal longitude,
                             String address, boolean isRealtime) {
        this.sender = sender;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.isRealtime = isRealtime;
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
}
