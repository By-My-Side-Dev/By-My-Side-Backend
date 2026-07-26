package com.gyeote.gyeote_api.domain.user.service;

import com.gyeote.gyeote_api.domain.user.dto.RealtimeLocationSettingRequest;
import com.gyeote.gyeote_api.domain.user.dto.RealtimeLocationSettingResponse;
import com.gyeote.gyeote_api.domain.user.dto.UserProfileResponse;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserPhone;
import com.gyeote.gyeote_api.domain.user.repository.UserPhoneRepository;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.exception.CommonErrorCode;
import com.gyeote.gyeote_api.global.security.PhoneCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserPhoneRepository userPhoneRepository;
    private final PhoneCryptoService phoneCryptoService;

    // [유저] 내 정보 조회
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        UserPhone userPhone = userPhoneRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        String phoneNumber = phoneCryptoService.decrypt(userPhone.getPhoneEncrypted());
        return UserProfileResponse.from(
                user.getName(),
                phoneNumber,
                user.getProfileImageUrl(),
                user.isRealtimeLocationEnabled()
        );
    }

    // [유저] 실시간 위치 전송 설정 변경
    @Transactional
    public RealtimeLocationSettingResponse updateRealtimeLocation(Long userId, RealtimeLocationSettingRequest request) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        user.updateRealtimeLocationEnable(request.realtimeLocationEnable());

        return RealtimeLocationSettingResponse.of(
                user.isRealtimeLocationEnabled()
        );
    }

}
