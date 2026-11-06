package com.gyeote.gyeote_api.domain.emergency.scheduler;

import com.gyeote.gyeote_api.domain.emergency.entity.AlertLocations;
import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.emergency.repository.AlertLocationsRepository;
import com.gyeote.gyeote_api.domain.emergency.repository.EmergencyAlertsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertLocationCleanupScheduler {

    private final AlertLocationsRepository alertLocationsRepository;
    private final EmergencyAlertsRepository emergencyAlertsRepository;

    /**
     * 매시간 만료된 긴급 호출의 종료 시각을 기록하고, delete_at이 지난 위치 이력을 일괄 삭제한다.
     */
    @Scheduled(cron = "${emergency.location-cleanup.cron:0 0 * * * *}")
    @Transactional
    public void deleteExpiredLocations() {
        LocalDateTime now = LocalDateTime.now();
        int endedAlertCount = emergencyAlertsRepository.endExpiredAlerts(
                now.minusHours(EmergencyAlerts.EXPIRE_HOURS)
        );
        LocalDateTime cutoff = now.minusDays(AlertLocations.RETENTION_DAYS);
        int deletedCount = alertLocationsRepository.deleteExpiredLocations(cutoff);

        if (endedAlertCount > 0 || deletedCount > 0) {
            log.info("Emergency cleanup completed: endedAlerts={}, deletedLocations={}",
                    endedAlertCount, deletedCount);
        }
    }
}
