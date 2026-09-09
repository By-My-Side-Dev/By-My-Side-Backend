package com.gyeote.gyeote_api.domain.device.entity;

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
        name = "device_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uniq_device_token_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uniq_device_token_hash", columnNames = "token_hash")
        }
)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    @Column(name = "token_encrypted", nullable = false, length = 5000, columnDefinition = "VARBINARY(5000)")
    private byte[] tokenEncrypted;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DevicePlatform platform;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 사용자의 FCM 디바이스 토큰 정보를 생성합니다.
     *
     * @param user 토큰을 소유하는 사용자
     * @param deviceId 클라이언트가 관리하는 디바이스 식별자
     * @param tokenEncrypted 암호화된 FCM 등록 토큰
     * @param tokenHash FCM 등록 토큰의 SHA-256 해시
     * @param platform 디바이스 플랫폼
     * @param appVersion 토큰 등록 시점의 애플리케이션 버전
     */
    public DeviceToken(
            User user,
            String deviceId,
            byte[] tokenEncrypted,
            String tokenHash,
            DevicePlatform platform,
            String appVersion
    ) {
        this.user = user;
        this.deviceId = deviceId;
        this.tokenEncrypted = tokenEncrypted.clone();
        this.tokenHash = tokenHash;
        this.platform = platform;
        this.appVersion = appVersion;
    }

    /**
     * 현재 사용자의 FCM 디바이스 토큰 정보를 최신 값으로 갱신합니다.
     *
     * @param deviceId 클라이언트가 관리하는 디바이스 식별자
     * @param tokenEncrypted 암호화된 FCM 등록 토큰
     * @param tokenHash FCM 등록 토큰의 SHA-256 해시
     * @param platform 디바이스 플랫폼
     * @param appVersion 토큰 등록 시점의 애플리케이션 버전
     */
    public void update(
            String deviceId,
            byte[] tokenEncrypted,
            String tokenHash,
            DevicePlatform platform,
            String appVersion
    ) {
        this.deviceId = deviceId;
        this.tokenEncrypted = tokenEncrypted.clone();
        this.tokenHash = tokenHash;
        this.platform = platform;
        this.appVersion = appVersion;
    }

    /**
     * 동일한 FCM 토큰의 소유권을 새로운 사용자에게 이전하고 디바이스 정보를 갱신합니다.
     *
     * <p>요청된 토큰과 현재 엔티티의 토큰이 동일한 경우에 사용하므로 기존 토큰 해시는
     * 변경하지 않습니다.</p>
     *
     * @param user 토큰을 새로 소유할 사용자
     * @param deviceId 새 소유자가 등록한 디바이스 식별자
     * @param tokenEncrypted 암호화된 FCM 등록 토큰
     * @param platform 디바이스 플랫폼
     * @param appVersion 토큰 등록 시점의 애플리케이션 버전
     */
    public void transferTo(
            User user,
            String deviceId,
            byte[] tokenEncrypted,
            DevicePlatform platform,
            String appVersion
    ) {
        this.user = user;
        this.deviceId = deviceId;
        this.tokenEncrypted = tokenEncrypted.clone();
        this.platform = platform;
        this.appVersion = appVersion;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    /**
     * 저장된 암호화 토큰의 방어적 복사본을 반환합니다.
     *
     * @return 암호화된 FCM 등록 토큰의 복사본
     */
    public byte[] getTokenEncrypted() {
        return tokenEncrypted.clone();
    }
}
