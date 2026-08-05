package com.gyeote.gyeote_api.domain.auth.service;

import com.gyeote.gyeote_api.domain.auth.dto.SignupRequest;
import com.gyeote.gyeote_api.domain.user.entity.UserPhone;
import com.gyeote.gyeote_api.domain.user.repository.UserPhoneRepository;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.security.OnboardingTokenProvider;
import com.gyeote.gyeote_api.global.security.PhoneCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Test
    void allowsDifferentAccountsToUseSamePhoneNumber() {
        SignupService signupService = new SignupService(
                userRepository,
                userPhoneRepository,
                passwordEncoder,
                phoneCryptoService,
                onboardingTokenProvider,
                authService
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

    private SignupRequest request(String loginId, String phone) {
        return new SignupRequest(
                "테스트 사용자",
                loginId,
                "password123",
                phone,
                true,
                true,
                true,
                true
        );
    }
}
