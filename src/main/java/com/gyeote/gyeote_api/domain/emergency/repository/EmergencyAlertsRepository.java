package com.gyeote.gyeote_api.domain.emergency.repository;

import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmergencyAlertsRepository extends JpaRepository<EmergencyAlerts, Long> {
    Optional<EmergencyAlerts> findByIdAndSenderId(Long alertId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select alert
            from EmergencyAlerts alert
            where alert.sender.id = :senderId
              and alert.endedAt is null
              and exists (
                    select recipient.id
                    from AlertRecipient recipient
                    where recipient.emergencyAlert.id = alert.id
                      and recipient.archivedAt is null
                      and recipient.createdAt <= :now
                      and recipient.expiresAt > :now
                      and recipient.deleteAt > :now
              )
            order by alert.createdAt desc, alert.id desc
            """)
    List<EmergencyAlerts> findReusableBySenderId(
            @Param("senderId") Long senderId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select alert
            from EmergencyAlerts alert
            where alert.id = :alertId
              and alert.sender.id = :userId
            """)
    Optional<EmergencyAlerts> findByIdAndSenderIdForUpdate(
            @Param("alertId") Long alertId,
            @Param("userId") Long userId
    );

    boolean existsByIdAndSenderId(Long alertId, Long userId);
}
