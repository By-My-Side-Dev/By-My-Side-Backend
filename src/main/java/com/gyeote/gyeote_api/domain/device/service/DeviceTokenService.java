package com.gyeote.gyeote_api.domain.device.service;

import com.gyeote.gyeote_api.domain.device.dto.DeviceTokenUpsertRequest;
import com.gyeote.gyeote_api.domain.device.entity.DeviceToken;
import com.gyeote.gyeote_api.domain.device.repository.DeviceTokenRepository;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.exception.CommonErrorCode;
import com.gyeote.gyeote_api.global.security.DeviceTokenCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 사용자의 FCM 디바이스 토큰을 등록하고 해제하는 서비스입니다.
 *
 * <p>원문 토큰은 암호화하여 저장하고, 토큰 소유권 확인과 중복 방지를 위해
 * 별도의 해시값을 사용합니다. 하나의 FCM 토큰이 다른 사용자에게 등록되면
 * 해당 토큰의 소유권을 현재 사용자에게 이전합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;
    private final DeviceTokenCryptoService cryptoService;

    /**
     * 사용자의 디바이스 토큰을 신규 등록하거나 최신 정보로 갱신합니다.
     *
     * <p>동일한 토큰을 다른 사용자가 보유하고 있으면 기존 사용자의 토큰 정보를
     * 현재 사용자에게 이전합니다. 사용자가 존재하지 않으면 예외가 발생합니다.</p>
     *
     * @param userId 토큰을 등록할 사용자 ID
     * @param request FCM 토큰과 디바이스 정보를 담은 요청
     * @throws BusinessException 탈퇴하지 않은 사용자를 찾을 수 없는 경우
     */
    @Transactional
    public void register(Long userId, DeviceTokenUpsertRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));
        String hash = cryptoService.hash(request.fcmToken());
        byte[] encrypted = cryptoService.encrypt(request.fcmToken());

        Optional<DeviceToken> tokenOwner = deviceTokenRepository.findByTokenHashForUpdate(hash);
        Optional<DeviceToken> current = deviceTokenRepository.findByUserIdForUpdate(userId);

        // 동일한 토큰이 다른 사용자에게 등록되어 있으면 소유권을 현재 사용자에게 이전한다.
        if (tokenOwner.isPresent() && !tokenOwner.get().getUser().getId().equals(userId)) {
            if (current.isPresent()) {
                DeviceToken deviceToken = current.get();
                deviceTokenRepository.delete(deviceToken);
                deviceTokenRepository.flush();
            }
            // user_id 유니크 제약 충돌을 방지하기 위해 기존 토큰 삭제를 먼저 반영한다.
            tokenOwner.get().transferTo(
                    user,
                    request.deviceId(),
                    encrypted,
                    request.platform(),
                    request.appVersion()
            );
            return;
        }

        DeviceToken target;
        if (tokenOwner.isPresent()) {
            target = tokenOwner.get();
        } else if (current.isPresent()) {
            target = current.get();
        } else {
            target = null;
        }

        if (target == null) {
            deviceTokenRepository.save(
                    new DeviceToken(
                            user,
                            request.deviceId(),
                            encrypted,
                            hash,
                            request.platform(),
                            request.appVersion()
                    )
            );
        } else {
            target.update(request.deviceId(), encrypted, hash, request.platform(), request.appVersion());
        }
    }

    /**
     * 사용자에게 등록된 현재 디바이스의 토큰을 해제합니다.
     *
     * <p>일치하는 토큰이 없어도 예외 없이 종료됩니다.</p>
     *
     * @param userId 토큰을 해제할 사용자 ID
     * @param deviceId 해제할 디바이스 식별자
     */
    @Transactional
    public void unregister(Long userId, String deviceId) {
        deviceTokenRepository.deleteCurrentDevice(userId, deviceId);
    }
}
