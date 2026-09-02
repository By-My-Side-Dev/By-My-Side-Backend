package com.gyeote.gyeote_api.domain.user.service;

import com.gyeote.gyeote_api.domain.auth.exception.SignupErrorCode;
import com.gyeote.gyeote_api.domain.user.dto.*;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserPhone;
import com.gyeote.gyeote_api.domain.user.exception.UserErrorCode;
import com.gyeote.gyeote_api.domain.user.repository.UserPhoneRepository;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.exception.CommonErrorCode;
import com.gyeote.gyeote_api.global.security.PhoneCryptoService;
import com.gyeote.gyeote_api.global.storage.ObjectStorage;
import com.gyeote.gyeote_api.global.storage.ObjectUploadRequest;
import com.gyeote.gyeote_api.global.storage.ProfileImageUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserPhoneRepository userPhoneRepository;
    private final PhoneCryptoService phoneCryptoService;
    private final ObjectStorage objectStorage;
    private final Tika tika;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    // [전화번호] 전화번호 검증 전역 변수
    private static final int MIN_PHONE_DIGITS = 10;
    private static final int MAX_PHONE_DIGITS = 15;

    // [파일] 이미지 파일 검증(tika)
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_PROFILE_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;

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
        String profileImageUrl = profileImageUrlResolver.resolve(user);

        return UserProfileResponse.from(
                user.getName(),
                user.getLoginId(),
                phoneNumber,
                profileImageUrl,
                user.isRealtimeLocationEnabled(),
                user.getPlan()
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
            byte[] encrypt = phoneCryptoService.encrypt(normalizePhoneNumber);
            userPhone.updatePhoneNumber(encrypt, newPhoneHash);
        }

        return UserProfileUpdateResponse.from(user.getName(), normalizePhoneNumber);
    }

    /**
     * 유저 프로필 사진 업데이트
     * @param userId 유저 아이디
     * @param request 유저 프로필 이미지 업데이트 정보
     */
    @Transactional
    public void updateProfileImage(Long userId, ProfileImageRequest request) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));
        String previousObjectKey = user.getProfileImageKey();

        MultipartFile image = request.image();
        String contentType = validateAndGetContentType(image);
        String extension = extensionFrom(contentType);
        String newObjectKey = createProfileImageObjectKey(user.getUuid(), extension);

        try (InputStream contentStream = image.getInputStream()) {
            objectStorage.upload(
                    ObjectUploadRequest.from(
                            newObjectKey,
                            contentStream,
                            contentType
                    )
            );
        } catch (IOException e) {
            log.error("Azure Blob 업로드 실패 = {}", e.getMessage());
            throw new BusinessException(UserErrorCode.USER_PROFILE_IMAGE_READ_FAILED);
        }

        registerProfileImageCleanup(previousObjectKey, newObjectKey);
        user.updateProfileImageKey(newObjectKey);
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

    /**
     * 전화번호 검증 메소드
     * @param normalizedPhone 문자열 전화번호
     */
    private void validatePhone(String normalizedPhone){
        // [검증] 전화번호 길이 검증
        if (normalizedPhone.length() < MIN_PHONE_DIGITS || MAX_PHONE_DIGITS < normalizedPhone.length()) {
            throw new BusinessException(SignupErrorCode.SIGNUP_INVALID_PHONE);
        }
    }

    /**
     * 이미지 파일 검증 메소드
     * @param image 이미지 파일
     * @return 파일 확장자({@code .jpg}, {@code .png})반환
     */
    private String validateAndGetContentType(MultipartFile image) {
        // [검증] 요청한 파일이 비어있는 경우
        if (image == null || image.isEmpty()) {
            throw new BusinessException(UserErrorCode.USER_PROFILE_IMAGE_EMPTY);
        }
        // [검증] 파일이 최대 크기를 초과하는 경우(10MB 제한)
        if (image.getSize() > MAX_PROFILE_IMAGE_SIZE_BYTES) {
            throw new BusinessException(UserErrorCode.USER_PROFILE_IMAGE_MAX);
        }

        // [검증] 파일의 확장자 설정
        try (InputStream contentStream = image.getInputStream()) {
            String detectedContentType = tika.detect(contentStream);
            if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(detectedContentType)) {
                throw new BusinessException(UserErrorCode.USER_PROFILE_IMAGE_UNSUPPORTED_TYPE);
            }

            return detectedContentType;
        } catch (IOException e) {
            log.error("프로필 검증에서 이미지 파일 읽기 실패 = {}", e.getMessage());
            throw new BusinessException(UserErrorCode.USER_PROFILE_IMAGE_READ_FAILED);
        }
    }

    /**
     * 확장자 분류 메소드
     * @param contentType 문자열 파일 확장자
     * @return 정규화된 확장자
     */
    private String extensionFrom(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new BusinessException(UserErrorCode.USER_PROFILE_IMAGE_UNSUPPORTED_TYPE);
        };
    }

    /**
     * 프로필 이미지 객체키 생성 메소드
     * @param userUuid 유저 고유 아이디
     * @param extension 파일 확장자
     * @return Azure Storage 저장 경로 반환
     */
    private String createProfileImageObjectKey(String userUuid, String extension) {
        return "profile/%s/%s.%s".formatted(
                userUuid,
                UUID.randomUUID(),
                extension
        );
    }

    /**
     * 프로필 이미지 변경 트랜잭션의 결과에 따라 기존 이미지 또는 신규 이미지를 정리한다.
     * 트랜잭션이 커밋되면 기존 이미지를 삭제하고, 롤백되면 새로 업로드한 이미지를 삭제한다.
     *
     * @param previousObjectKey 기존 프로필 이미지 객체 키
     * @param newObjectKey 새로 업로드한 프로필 이미지 객체 키
     */
    private void registerProfileImageCleanup(String previousObjectKey, String newObjectKey) {
        try {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            if (previousObjectKey != null) {
                                deleteProfileImageSafely(previousObjectKey);
                            }
                        }

                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) {
                                deleteProfileImageSafely(newObjectKey);
                            }
                        }
                    }
            );
        } catch (RuntimeException e) {
            deleteProfileImageSafely(newObjectKey);
            throw e;
        }
    }

    /**
     * 프로필 이미지 삭제 실패가 완료된 DB 트랜잭션에 영향을 주지 않도록 예외를 기록한다.
     *
     * @param objectKey 삭제할 프로필 이미지 객체 키
     */
    private void deleteProfileImageSafely(String objectKey) {
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException e) {
            log.error("프로필 이미지 삭제 실패. objectKey={}", objectKey, e);
        }
    }

}
