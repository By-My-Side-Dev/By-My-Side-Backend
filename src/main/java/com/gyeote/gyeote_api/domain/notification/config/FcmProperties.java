package com.gyeote.gyeote_api.domain.notification.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "fcm")
public class FcmProperties {

    @Min(value = 1, message = "fcm.max-attempts는 1 이상이어야 합니다.")
    private int maxAttempts;

    @Min(value = 1, message = "fcm.batch-size는 1 이상이어야 합니다.")
    private int batchSize;

    private String projectId;
    private final Scheduler scheduler = new Scheduler();

    @Getter
    @Setter
    public static class Scheduler {
        private long fixedDelayMs;
    }
}
