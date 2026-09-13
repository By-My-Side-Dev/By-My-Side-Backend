package com.gyeote.gyeote_api.domain.notification.repository;

import com.gyeote.gyeote_api.domain.notification.entity.FcmDeliveryLog;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FcmDeliveryLogRepository extends JpaRepository<FcmDeliveryLog, Long> {

    @Query(value = """
            SELECT delivery.* FROM fcm_delivery_log delivery
            WHERE delivery.created_at > :expiresBefore
              AND delivery.attempt_count < :maxAttempts
              AND ((delivery.status IN ('PENDING', 'RETRYABLE_FAILED') AND delivery.next_attempt_at <= :now)
                OR (delivery.status = 'PROCESSING' AND delivery.processing_started_at <= :staleBefore))
            ORDER BY delivery.next_attempt_at, delivery.id
            LIMIT :batchSize FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<FcmDeliveryLog> claimable(@Param("now") LocalDateTime now,
                                   @Param("staleBefore") LocalDateTime staleBefore,
                                   @Param("expiresBefore") LocalDateTime expiresBefore,
                                   @Param("maxAttempts") int maxAttempts,
                                   @Param("batchSize") int batchSize);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from FcmDeliveryLog delivery where delivery.id = :id")
    Optional<FcmDeliveryLog> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query(value = """
            UPDATE fcm_delivery_log SET status = 'EXPIRED', processing_started_at = NULL, updated_at = :now
            WHERE status IN ('PENDING', 'RETRYABLE_FAILED', 'PROCESSING') AND created_at <= :expiresBefore
            """, nativeQuery = true)
    int expireOld(@Param("now") LocalDateTime now,
                  @Param("expiresBefore") LocalDateTime expiresBefore);

    @Modifying
    @Query(value = """
            UPDATE fcm_delivery_log SET status = 'PERMANENT_FAILED', processing_started_at = NULL,
              last_error_code = 'ATTEMPTS_EXHAUSTED', updated_at = :now
            WHERE status = 'PROCESSING' AND attempt_count >= :maxAttempts AND processing_started_at <= :staleBefore
            """, nativeQuery = true)
    int failExhaustedStale(@Param("now") LocalDateTime now,
                           @Param("staleBefore") LocalDateTime staleBefore,
                           @Param("maxAttempts") int maxAttempts);
}
