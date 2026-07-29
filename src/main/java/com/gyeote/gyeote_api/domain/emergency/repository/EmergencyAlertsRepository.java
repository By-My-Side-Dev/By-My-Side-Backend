package com.gyeote.gyeote_api.domain.emergency.repository;

import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmergencyAlertsRepository extends JpaRepository<EmergencyAlerts, Long> {
    Optional<EmergencyAlerts> findEmergencyAlertsById(Long id);
}