package com.gyeote.gyeote_api.domain.user.service;

import com.gyeote.gyeote_api.domain.auth.exception.SignupErrorCode;
import com.gyeote.gyeote_api.domain.user.dto.*;
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

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserPhoneRepository userPhoneRepository;
    private final PhoneCryptoService phoneCryptoService;

    // [전화번호] 전화번호 검증 전역 변수
    private static final int MIN_PHONE_DIGITS = 10;
    private static final int MAX_PHONE_DIGITS = 15;

    /**
     * 내 정보 조회
     * @param userId 유저 아이디
     * @return 유저의 전체 정보 조회
     */
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        UserPhone userPhone = userPhoneRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        String phoneNumber = phoneCryptoService.decrypt(userPhone.getPhoneEncrypted());
        return UserProfileResponse.from(
                user.getName(),
                user.getLoginId(),
                phoneNumber,
                user.getProfileImageUrl(),
                user.isRealtimeLocationEnabled()
        );
    }

    /**
     * 실시간 위치 전송 토글
     * @param userId 유저 아이디
     * @param request 유저의 실시간 위치 전송 토글 On/Off 설정 정보
     * @return 실시간 위치 전송 토글 정보 반환
     */
    @Transactional
    public RealtimeLocationSettingResponse updateRealtimeLocation(Long userId, RealtimeLocationSettingRequest request) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        user.updateRealtimeLocationEnable(request.realtimeLocationEnable());

        return RealtimeLocationSettingResponse.of(
                user.isRealtimeLocationEnabled()
        );
    }

    /**
     * 유저 프로필 업데이트
     * @param userId 유저 아이디
     * @param request 유저 프로필 업데이트 정보
     * @return 업데이트 된 유저의 정보
     */
    @Transactional
    public UserProfileUpdateResponse updateProfile(Long userId, UserProfileUpdateRequest request) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        UserPhone userPhone = userPhoneRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        if (!user.getName().equals(request.name())) {
            user.updateUserName(request.name());
        }

        String normalizePhoneNumber = phoneCryptoService.normalize(request.phoneNumber());
        String currentPhoneNumber = phoneCryptoService.decrypt(userPhone.getPhoneEncrypted());
        if (!currentPhoneNumber.equals(normalizePhoneNumber)) {
            validatePhone(normalizePhoneNumber);

            String newPhoneHash = phoneCryptoService.hash(normalizePhoneNumber);
            userPhoneRepository.findByActivePhoneHash(newPhoneHash).ifPresent(this::rejectOccupiedPhone);

            byte[] encrypt = phoneCryptoService.encrypt(normalizePhoneNumber);
            userPhone.updatePhoneNumber(encrypt, newPhoneHash);
        }

        return UserProfileUpdateResponse.from(user.getName(), normalizePhoneNumber);
    }

    /**
     * 회원 탈퇴(soft delete)</br>
     * soft delete 정책에 의해서 유저와 유저 전화번에 deleteAt에 삭제 시간을 기록한다.
     * @param userId 유저의 아이디
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        UserPhone userPhone = userPhoneRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        LocalDateTime deletedAt = LocalDateTime.now();

        user.softDeleteUser(deletedAt);
        userPhone.softDeletePhoneNumber(deletedAt);
    }

    private void validatePhone(String normalizedPhone){
        // [검증] 전화번호 길이 검증
        if (normalizedPhone.length() < MIN_PHONE_DIGITS || MAX_PHONE_DIGITS < normalizedPhone.length()) {
            throw new BusinessException(SignupErrorCode.SIGNUP_INVALID_PHONE);
        }
    }

    private void rejectOccupiedPhone(UserPhone activePhone) {
        throw new BusinessException(SignupErrorCode.SIGNUP_DUPLICATED_PHONE);
    }

}
