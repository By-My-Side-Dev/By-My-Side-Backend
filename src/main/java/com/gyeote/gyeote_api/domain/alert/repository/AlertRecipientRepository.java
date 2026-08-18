package com.gyeote.gyeote_api.domain.alert.repository;

import com.gyeote.gyeote_api.domain.alert.entity.AlertRecipient;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlertRecipientRepository extends JpaRepository<AlertRecipient, Long> {
    boolean existsByEmergencyAlert_Id(Long alertId);
    @Query(
            value = """
                    select recipient
                    from AlertRecipient recipient
                    join fetch recipient.emergencyAlert alert
                    join fetch alert.sender sender
                    where recipient.receiver.id = :receiverId
                      and recipient.archivedAt is null
                      and recipient.expiresAt > :now
                      and sender.deletedAt is null
                    order by recipient.createdAt desc
                    """,
            countQuery = """
                    select count(recipient)
                    from AlertRecipient recipient
                    join recipient.emergencyAlert alert
                    join alert.sender sender
                    where recipient.receiver.id = :receiverId
                      and recipient.archivedAt is null
                      and recipient.expiresAt > :now
                      and sender.deletedAt is null
                    """
    )
    Page<AlertRecipient> findActivePageByReceiverId(
            @Param("receiverId") Long receiverId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    /**
     * 내가 받은 유효한(만료·보관 전) 긴급 알림 수신 목록을 발신자·호출 정보와 함께 조회한다.
     */
    @Query("""
            select recipient
            from AlertRecipient recipient
            join fetch recipient.emergencyAlert alert
            join fetch alert.sender sender
            where recipient.receiver.id = :receiverId
              and recipient.archivedAt is null
              and recipient.expiresAt > :now
              and sender.deletedAt is null
            order by recipient.createdAt desc
            """)
    List<AlertRecipient> findActiveByReceiverId(
            @Param("receiverId") Long receiverId,
            @Param("now") LocalDateTime now
    );

    /**
     * 수신 알림 1건을 발신자·호출 정보와 함께 조회한다.
     */
    @Query("""
            select recipient
            from AlertRecipient recipient
            join fetch recipient.emergencyAlert alert
            join fetch alert.sender
            where recipient.id = :recipientId
            """)
    Optional<AlertRecipient> findWithAlertById(@Param("recipientId") Long recipientId);

    @Query("""
            select recipient
            from AlertRecipient recipient
            join fetch recipient.emergencyAlert alert
            join fetch alert.sender
            where alert.id = :alertId
              and recipient.receiver.id = :receiverId
            """)
    Optional<AlertRecipient> findWithAlertByAlertIdAndReceiverId(
            @Param("alertId") Long alertId,
            @Param("receiverId") Long receiverId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select recipient
            from AlertRecipient recipient
            join fetch recipient.emergencyAlert alert
            join fetch alert.sender
            where alert.id = :alertId
              and recipient.receiver.id = :receiverId
            """)
    Optional<AlertRecipient> findWithAlertByAlertIdAndReceiverIdForUpdate(
            @Param("alertId") Long alertId,
            @Param("receiverId") Long receiverId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AlertRecipient recipient
            set recipient.archivedAt = :now
            where recipient.receiver.id = :receiverId
              and recipient.archivedAt is null
              and recipient.expiresAt <= :now
              and recipient.deleteAt > :now
            """)
    int archiveExpiredByReceiverId(
            @Param("receiverId") Long receiverId,
            @Param("now") LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AlertRecipient recipient
            set recipient.archivedAt = :now
            where recipient.archivedAt is null
              and recipient.expiresAt <= :now
              and recipient.deleteAt > :now
            """)
    int archiveExpired(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from AlertRecipient recipient
            where recipient.deleteAt <= :now
            """)
    int deleteExpired(@Param("now") LocalDateTime now);

    @Query("""
            select recipient
            from AlertRecipient recipient
            join fetch recipient.emergencyAlert alert
            join fetch alert.sender sender
            where recipient.receiver.id = :receiverId
              and recipient.archivedAt is not null
              and recipient.deleteAt > :now
              and sender.deletedAt is null
            order by recipient.createdAt desc
            """)
    List<AlertRecipient> findHistoryByReceiverId(
            @Param("receiverId") Long receiverId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select count(recipient) > 0
            from AlertRecipient recipient
            where recipient.emergencyAlert.id = :alertId
              and recipient.receiver.id = :receiverId
              and recipient.archivedAt is null
              and recipient.expiresAt > :now
              and recipient.realtimeStoppedAt is null
              and recipient.emergencyAlert.isRealtime = true
              and recipient.emergencyAlert.endedAt is null
              and exists (
                    select contact.id
                    from Contact contact
                    where contact.user.id = :receiverId
                      and contact.contactUser.id = recipient.emergencyAlert.sender.id
                      and contact.receiveRealtime = true
              )
            """)
    boolean existsActiveRealtimeSubscription(
            @Param("alertId") Long alertId,
            @Param("receiverId") Long receiverId,
            @Param("now") LocalDateTime now
    );
}
