package com.gyeote.gyeote_api.domain.contact.entity;

import com.gyeote.gyeote_api.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "contacts", uniqueConstraints =
        @UniqueConstraint(name = "uniq_user_contact", columnNames = {"user_id", "contact_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_user_id", nullable = false)
    private User contactUser;

    @Column(name = "receive_realtime", nullable = false)
    private boolean receiveRealtime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Contact(User user, User contactUser) {
        this.user = user;
        this.contactUser = contactUser;
        this.receiveRealtime = true;
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public void updateReceiveRealtime(boolean receiveRealtime) {
        this.receiveRealtime = receiveRealtime;
    }
}
