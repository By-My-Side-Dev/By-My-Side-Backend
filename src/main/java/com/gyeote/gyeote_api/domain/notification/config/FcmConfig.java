package com.gyeote.gyeote_api.domain.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.gyeote.gyeote_api.global.security.DeviceTokenCryptoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class FcmConfig {

    @Bean
    FirebaseApp firebaseApp(FcmProperties properties, DeviceTokenCryptoService cryptoService) throws IOException {
        // [검증] 환경변수에 디바이스 토큰 값이 들어가 있지 않으면 오류
        if (!cryptoService.isConfigured()) {
            throw new IllegalStateException("FCM_TOKEN_ENC_KEY를 구성해야 합니다.");
        }
        // [검증] 환경변수에 프로젝트 아이디 문자열이 들어가 있지 않으면 오류
        if (properties.getProjectId() == null || properties.getProjectId().isBlank()) {
            throw new IllegalStateException("FIREBASE_PROJECT_ID를 반드시 구성해야 합니다.");
        }
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        // [검증] 인증정보를 검증하고 FCM 초기화
        if (credentials instanceof ServiceAccountCredentials serviceAccount
                && serviceAccount.getProjectId() != null
                && !properties.getProjectId().equals(serviceAccount.getProjectId())) {
            throw new IllegalStateException("FIREBASE_PROJECT_ID가 서비스 계정 프로젝트와 일치하지 않습니다.");
        }
        // 파이어베이스 초기화 설정 객체 생성
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(properties.getProjectId())
                .build();
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
