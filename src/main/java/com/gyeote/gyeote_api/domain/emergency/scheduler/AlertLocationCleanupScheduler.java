package com.gyeote.gyeote_api.domain.emergency.scheduler;

import com.gyeote.gyeote_api.domain.emergency.entity.AlertLocations;
import com.gyeote.gyeote_api.domain.emergency.repository.AlertLocationsRepository;
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

    /**
     * 매시간 delete_at이 지난 위치 이력을 일괄 삭제한다.
     */
    @Scheduled(cron = "${emergency.location-cleanup.cron:0 0 * * * *}")
    @Transactional
    public void deleteExpiredLocations() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(AlertLocations.RETENTION_DAYS);
        int deletedCount = alertLocationsRepository.deleteExpiredLocations(cutoff);

        if (deletedCount > 0) {
            log.info("Expired alert locations deleted: count={}", deletedCount);
        }
    }
}
