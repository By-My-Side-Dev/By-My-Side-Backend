package com.gyeote.gyeote_api.domain.emergency.repository;

import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmergencyAlertsRepository extends JpaRepository<EmergencyAlerts, Long> {
    Optional<EmergencyAlerts> findByIdAndSenderId(Long alertId, Long userId);

    boolean existsByIdAndSenderId(Long alertId, Long userId);
}
