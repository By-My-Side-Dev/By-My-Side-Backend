package com.gyeote.gyeote_api.domain.user.entity;

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

import java.time.LocalDateTime;

/**
 * 전화번호 (DB 명세서 v1.1.0 — 3.2 user_phones).
 * 암호화 PII를 users에서 분리 보관한다.
 * phone_encrypted(AES-GCM 랜덤 IV, 복호화용) + phone_hash(HMAC 블라인드 인덱스, 조회·매칭용).
 * 평문 전화번호는 어디에도 저장하지 않는다.
 * 탈퇴 시각은 이 테이블의 deleted_at 한 곳에서만 관리한다(soft delete 일원화).
 */
@Entity
@Table(name = "user_phones", indexes = @Index(name = "idx_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPhone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "phone_encrypted", nullable = false, columnDefinition = "varbinary(255)")
    private byte[] phoneEncrypted;

    @Column(name = "phone_hash", nullable = false, columnDefinition = "char(64)")
    private String phoneHash;

    /**
     * 연결(활성) = NULL / 탈퇴·해제 시 해당 시각. 활성 UNIQUE 슬롯이 즉시 해제되어 같은 번호 재가입이 가능해진다.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 활성일 때만 phone_hash가 들어가는 MySQL 생성 컬럼.
     * UNIQUE로 번호당 활성 1개를 DB 레벨에서 강제하면서, 탈퇴 행(NULL)은 중복을 허용한다.
     */
    @Column(name = "active_phone_hash", insertable = false, updatable = false,
            columnDefinition = "char(64) GENERATED ALWAYS AS (IF(deleted_at IS NULL, phone_hash, NULL)) STORED UNIQUE")
    private String activePhoneHash;

    /**
     * 활성일 때만 user_id가 들어가는 생성 컬럼. UNIQUE로 유저당 활성 번호 1개 강제.
     */
    @Column(name = "active_user_id", insertable = false, updatable = false,
            columnDefinition = "bigint GENERATED ALWAYS AS (IF(deleted_at IS NULL, user_id, NULL)) STORED UNIQUE")
    private Long activeUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private UserPhone(User user, byte[] phoneEncrypted, String phoneHash) {
        this.user = user;
        this.phoneEncrypted = phoneEncrypted;
        this.phoneHash = phoneHash;
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * 번호 점유 해제(탈퇴·reclaim). 행은 이력으로 보존된다.
     */
    public void release(LocalDateTime releasedAt) {
        this.deletedAt = releasedAt;
    }
}
