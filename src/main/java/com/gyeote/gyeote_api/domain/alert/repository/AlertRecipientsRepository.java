package com.gyeote.gyeote_api.domain.alert.repository;

import com.gyeote.gyeote_api.domain.alert.entity.AlertRecipients;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlertRecipientsRepository extends JpaRepository<AlertRecipients, Long> {

    /**
     * 내가 받은 유효한(만료·보관 전) 긴급 알림 수신 목록을 발신자·호출 정보와 함께 조회한다.
     */
    @Query("""
            select recipient
            from AlertRecipients recipient
            join fetch recipient.emergencyAlert alert
            join fetch alert.sender sender
            where recipient.receiver.id = :receiverId
              and recipient.archivedAt is null
              and recipient.expiresAt > :now
              and sender.deletedAt is null
            order by recipient.createdAt desc
            """)
    List<AlertRecipients> findActiveByReceiverId(
            @Param("receiverId") Long receiverId,
            @Param("now") LocalDateTime now
    );

    /**
     * 수신 알림 1건을 발신자·호출 정보와 함께 조회한다.
     */
    @Query("""
            select recipient
            from AlertRecipients recipient
            join fetch recipient.emergencyAlert alert
            join fetch alert.sender
            where recipient.id = :recipientId
            """)
    Optional<AlertRecipients> findWithAlertById(@Param("recipientId") Long recipientId);
}
