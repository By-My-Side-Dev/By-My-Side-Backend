package com.gyeote.gyeote_api.domain.auth.service;

import com.gyeote.gyeote_api.domain.auth.dto.SignupRequest;
import com.gyeote.gyeote_api.domain.auth.dto.OnboardingInfo;
import com.gyeote.gyeote_api.domain.auth.dto.SocialSignupCompleteRequest;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserPhone;
import com.gyeote.gyeote_api.domain.user.repository.UserPhoneRepository;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.security.OnboardingTokenProvider;
import com.gyeote.gyeote_api.global.security.PhoneCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPhoneRepository userPhoneRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PhoneCryptoService phoneCryptoService;
    @Mock
    private OnboardingTokenProvider onboardingTokenProvider;
    @Mock
    private AuthService authService;
    @Mock
    private EmailVerificationService emailVerificationService;

    @Test
    void allowsDifferentAccountsToUseSamePhoneNumber() {
        SignupService signupService = new SignupService(
                userRepository,
                userPhoneRepository,
                passwordEncoder,
                phoneCryptoService,
                onboardingTokenProvider,
                authService,
                emailVerificationService
        );
        String phone = "01012345678";
        when(phoneCryptoService.normalize(phone)).thenReturn(phone);
        when(phoneCryptoService.hash(phone)).thenReturn("phone-hash");
        when(phoneCryptoService.encrypt(phone)).thenReturn(new byte[]{1, 2, 3});
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");

        assertDoesNotThrow(() -> {
            signupService.signup(request("first-user", phone));
            signupService.signup(request("second-user", phone));
        });

        verify(userPhoneRepository, org.mockito.Mockito.times(2)).save(any(UserPhone.class));
    }

    @Test
    void newRegularAccountStartsWithRealtimeLocationDisabled() {
        SignupService service = new SignupService(userRepository, userPhoneRepository, passwordEncoder,
                phoneCryptoService, onboardingTokenProvider, authService, emailVerificationService);
        when(phoneCryptoService.normalize("01012345678")).thenReturn("01012345678");
        when(phoneCryptoService.hash("01012345678")).thenReturn("phone-hash");
        when(phoneCryptoService.encrypt("01012345678")).thenReturn(new byte[]{1, 2, 3});
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        service.signup(request("regular-user", "01012345678"));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(user.capture());
        assertFalse(user.getValue().isRealtimeLocationEnabled());
    }

    @Test
    void newSocialAccountStartsWithRealtimeLocationDisabled() {
        SignupService service = new SignupService(userRepository, userPhoneRepository, passwordEncoder,
                phoneCryptoService, onboardingTokenProvider, authService, emailVerificationService);
        when(onboardingTokenProvider.parseOnboardingToken("onboarding-token"))
                .thenReturn(new OnboardingInfo("kakao", "provider-user"));
        when(phoneCryptoService.normalize("01012345678")).thenReturn("01012345678");
        when(phoneCryptoService.hash("01012345678")).thenReturn("phone-hash");
        when(phoneCryptoService.encrypt("01012345678")).thenReturn(new byte[]{1, 2, 3});
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        service.completeSocialSignup(socialRequest());

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertFalse(user.getValue().isRealtimeLocationEnabled());
    }

    @Test
    void linkingSocialAccountPreservesEnabledRealtimeLocationSetting() {
        SignupService service = new SignupService(userRepository, userPhoneRepository, passwordEncoder,
                phoneCryptoService, onboardingTokenProvider, authService, emailVerificationService);
        User existingUser = User.builder().name("기존 사용자").loginId("social-user")
                .passwordHash("encoded-password").build();
        existingUser.activate();
        existingUser.updateRealtimeLocationEnable(true);
        when(onboardingTokenProvider.parseOnboardingToken("onboarding-token"))
                .thenReturn(new OnboardingInfo("kakao", "provider-user"));
        when(userRepository.findByLoginIdAndDeletedAtIsNull("social-user"))
                .thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        service.completeSocialSignup(socialRequest());

        assertTrue(existingUser.isRealtimeLocationEnabled());
    }

    private SocialSignupCompleteRequest socialRequest() {
        return new SocialSignupCompleteRequest("onboarding-token", "사용자", "social-user",
                "password123", "01012345678", true, true, true, true);
    }

    private SignupRequest request(String loginId, String phone) {
        return new SignupRequest(
                "테스트 사용자",
                loginId,
                loginId + "@example.com",
                "email-verification-token",
                "password123",
                phone,
                true,
                true,
                true,
                true
        );
    }
}
