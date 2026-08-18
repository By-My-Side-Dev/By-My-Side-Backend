package com.gyeote.gyeote_api.domain.alert.scheduler;

import com.gyeote.gyeote_api.domain.alert.repository.AlertRecipientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRecipientCleanupScheduler {

    private final AlertRecipientRepository alertRecipientRepository;

    /**
     * 서버 기본 시간대를 기준으로 매일 01:00에 수신 알림 보관 및 삭제 작업을 실행한다.
     * <p>
     * 수신 후 24시간이 지난 활성 알림은 {@code archived_at}을 기록해 이전 알림 이력으로
     * 전환하고, {@code delete_at}이 지난 보관 알림은 데이터베이스에서 물리 삭제한다.
     * 기본 실행 시각은 매일 01:00이며, {@code emergency.alert-recipient-cleanup.cron}
     * 설정으로 실행 주기를 변경할 수 있다.
     * </p>
     */
    @Scheduled(cron = "${emergency.alert-recipient-cleanup.cron:0 0 1 * * *}")
    @Transactional
    public void archiveAndDeleteExpiredRecipients() {
        LocalDateTime now = LocalDateTime.now();
        int archivedCount = alertRecipientRepository.archiveExpired(now);
        int deletedCount = alertRecipientRepository.deleteExpired(now);

        if (archivedCount > 0 || deletedCount > 0) {
            log.info("Alert recipient cleanup completed: archived={}, deleted={}",
                    archivedCount, deletedCount);
        }
    }
}
