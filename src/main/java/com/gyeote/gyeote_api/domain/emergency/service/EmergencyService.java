package com.gyeote.gyeote_api.domain.emergency.service;

import com.gyeote.gyeote_api.domain.emergency.dto.EmergencyRequest;
import com.gyeote.gyeote_api.domain.emergency.dto.EmergencyResponse;
import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.emergency.repository.EmergencyAlertsRepository;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmergencyService {

    private final UserRepository userRepository;
    private final EmergencyAlertsRepository emergencyAlertsRepository;

    /**
     * 도움 요청 생성
     * @param userId 유저 아이디
     * @param request 위도, 경도, 주소를 받는다.
     * @return 알림 아이디를 반환한다.
     */
    @Transactional
    public EmergencyResponse createEmergency(Long userId, EmergencyRequest request) {
        User sender = userRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        // [응급 알림 생성] 최초 알림 생성
        EmergencyAlerts emergencyAlerts = EmergencyAlerts.builder()
                .sender(sender)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .address(request.address())
                .isRealtime(sender.isRealtimeLocationEnabled())
                .build();

        emergencyAlertsRepository.save(emergencyAlerts);

        return EmergencyResponse.from(emergencyAlerts.getId());
    }

    /**
     * 도움 요청 종료
     * @param userId 유저 아이디
     * @param alertId 알림 id를 파라미터로 받는다.
     */
    @Transactional
    public void endEmergency(Long userId, Long alertId) {
        EmergencyAlerts emergencyAlerts = emergencyAlertsRepository.findEmergencyAlertsById(alertId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        // [검증] 현재 사용자가 같은 사용자 인지 검증
        if (!emergencyAlerts.getSender().getId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.COMMON_FORBIDDEN);
        }

        // [검증] 다시 종료 버튼을 눌러서 종료 시간을 덮어쓰는지 검증
        if (!(emergencyAlerts.getEndedAt() == null)) {
            throw new BusinessException(CommonErrorCode.COMMON_CONFLICT);
        }

        emergencyAlerts.end(LocalDateTime.now());
    }

}
