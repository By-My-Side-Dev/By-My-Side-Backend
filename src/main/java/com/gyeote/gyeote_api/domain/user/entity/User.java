package com.gyeote.gyeote_api.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uniq_login_id", columnNames = "login_id"),
        @UniqueConstraint(name = "uniq_provider", columnNames = {"provider", "provider_uid"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String uuid;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "login_id", length = 50)
    private String loginId;

    @Column(name = "password_hash", length = 72)
    private String passwordHash;

    @Column(length = 50)
    private String provider;

    @Column(name = "provider_uid")
    private String providerUid;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "realtime_location_enabled", nullable = false)
    private boolean realtimeLocationEnabled;

    @Column(name = "receive_contacts_only", nullable = false)
    private boolean receiveContactsOnly;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private User(String name, String loginId, String passwordHash,
                 String provider, String providerUid, String profileImageUrl) {
        this.name = name;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.providerUid = providerUid;
        this.profileImageUrl = profileImageUrl;
    }

    @PrePersist
    private void prePersist() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = UserStatus.DEACTIVE;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void updateLastLoginAt(LocalDateTime loginAt) {
        this.lastLoginAt = loginAt;
    }

    /**
     * 전화번호 인증까지 마친 가입 완료 시점에 호출한다.
     */
    public void activate() {
        this.status = UserStatus.ACTIVE;
    }
}
