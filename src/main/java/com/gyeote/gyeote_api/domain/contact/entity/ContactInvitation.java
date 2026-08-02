package com.gyeote.gyeote_api.domain.contact.entity;

import com.gyeote.gyeote_api.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "contact_invitations", indexes =
        @Index(name = "idx_sender_receiver", columnList = "sender_id,receiver_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactInvitationStatus status;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ContactInvitation(User sender, User receiver) {
        this.sender = sender;
        this.receiver = receiver;
    }

    @PrePersist
    private void prePersist() {
        if (status == null) status = ContactInvitationStatus.PENDING;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public void accept(LocalDateTime now) {
        status = ContactInvitationStatus.ACCEPTED;
        respondedAt = now;
    }

    public void reject(LocalDateTime now) {
        status = ContactInvitationStatus.REJECTED;
        respondedAt = now;
    }

    public void cancel(LocalDateTime now) {
        status = ContactInvitationStatus.CANCELED;
        respondedAt = now;
    }
}
