package com.gyeote.gyeote_api.global.config;

import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserPhone;
import com.gyeote.gyeote_api.domain.user.repository.UserPhoneRepository;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.security.JwtTokenProvider;
import com.gyeote.gyeote_api.global.security.PhoneCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * local 프로필 전용 개발용 시드 데이터.
 * spring.jpa.hibernate.ddl-auto=create라 로컬은 앱을 켤 때마다 스키마가 새로 만들어지므로,
 * 매번 시작 시 테스트 유저(id=1)를 다시 심고, 로그인 없이 바로 API를 호출해볼 수 있도록 매번 새 JWT를 발급해 파일로 저장한다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalLoginInitializer implements CommandLineRunner {

    private static final String TEST_LOGIN_ID = "testuser";
    private static final String TEST_PASSWORD = "password1234";
    private static final String TEST_PHONE = "01000000000";

    private static final Path TOKEN_FILE = Path.of("local-test-tokens.log");

    private final UserRepository userRepository;
    private final UserPhoneRepository userPhoneRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneCryptoService phoneCryptoService;
    private final JwtTokenProvider jwtTokenProvider;


    @Override
    @Transactional
    public void run(String... args) {
        User user = userRepository.findByLoginIdAndDeletedAtIsNull(TEST_LOGIN_ID)
                .orElseGet(this::createTestUser);

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        // Swagger의 Authorize 입력창은 bearer 스킴이라 토큰값만 넣으면 "Bearer "를 자동으로 붙여서 보낸다.
        String content = """
                [테스트유저] userId=%d, loginId=%s
                accessToken: %s
                refreshToken: %s

                """.formatted(user.getId(), TEST_LOGIN_ID, accessToken, refreshToken);

        try {
            Files.writeString(
                    TOKEN_FILE,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            log.info(
                    "테스트 계정 토큰을 {}에 저장했습니다. 터미널 자동 줄바꿈으로 토큰이 깨질 수 있으니, 콘솔 대신 이 파일에서 복사하세요.",
                    TOKEN_FILE.toAbsolutePath()
            );
        } catch (IOException e) {
            throw new UncheckedIOException("테스트 계정 토큰 파일 저장 실패", e);
        }
    }

    private User createTestUser() {
        User user = User.builder()
                .name("테스트유저")
                .loginId(TEST_LOGIN_ID)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .agreeLocation(true)
                .agreeTerms(true)
                .agreePrivacy(true)
                .agreeThirdParty(true)
                .build();
        user.activate();
        User savedUser = userRepository.save(user);

        String normalizedPhone = phoneCryptoService.normalize(TEST_PHONE);
        userPhoneRepository.save(UserPhone.builder()
                .user(savedUser)
                .phoneEncrypted(phoneCryptoService.encrypt(normalizedPhone))
                .phoneHash(phoneCryptoService.hash(normalizedPhone))
                .build());

        return savedUser;
    }
}
