package com.gyeote.gyeote_api.domain.notification.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

@Component
@RequiredArgsConstructor
public class FcmDatabasePrerequisiteValidator {
    private final DataSource dataSource;

    @PostConstruct
    void validateSkipLockedSupport() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!metadata.getDatabaseProductName().toLowerCase().contains("mysql")) {
                throw new IllegalStateException("FCM Outbox를 사용하려면 MySQL 8.0.1 이상 버전이 필요합니다.");
            }
            int major = metadata.getDatabaseMajorVersion();
            int minor = metadata.getDatabaseMinorVersion();
            int patch = parsePatch(metadata.getDatabaseProductVersion());
            if (major < 8 || (major == 8 && minor == 0 && patch < 1)) {
                throw new IllegalStateException("FCM Outbox는 SKIP LOCKED 기능을 사용하기 위해 MySQL 8.0.1 이상 버전이 필요합니다.");
            }
        }
    }

    private int parsePatch(String version) {
        String[] components = version.split("[.-]", 4);
        if (components.length < 3) return 0;
        try {
            return Integer.parseInt(components[2].replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
