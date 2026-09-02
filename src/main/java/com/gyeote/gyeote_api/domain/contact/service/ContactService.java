package com.gyeote.gyeote_api.domain.contact.service;

import com.gyeote.gyeote_api.domain.contact.dto.*;
import com.gyeote.gyeote_api.domain.contact.entity.*;
import com.gyeote.gyeote_api.domain.contact.exception.ContactErrorCode;
import com.gyeote.gyeote_api.domain.contact.repository.BlockedUserRepository;
import com.gyeote.gyeote_api.domain.contact.repository.ContactInvitationRepository;
import com.gyeote.gyeote_api.domain.contact.repository.ContactRepository;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserPhone;
import com.gyeote.gyeote_api.domain.user.entity.UserStatus;
import com.gyeote.gyeote_api.domain.user.repository.UserPhoneRepository;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.security.PhoneCryptoService;
import com.gyeote.gyeote_api.global.storage.ProfileImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactService {

    private final UserRepository userRepository;
    private final UserPhoneRepository userPhoneRepository;
    private final ContactInvitationRepository invitationRepository;
    private final ContactRepository contactRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final PhoneCryptoService phoneCryptoService;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    /**
     * 로그인 아이디로 활성 사용자를 검색한다.
     *
     * @param userId 검색을 요청한 사용자 아이디
     * @param request 검색할 로그인 아이디를 담은 요청
     * @return 검색된 사용자 정보
     */
    public UserSearchResponse searchUser(Long userId, UserSearchRequest request) {
        requireActiveUser(userId);
        User target = userRepository.findByLoginIdAndDeletedAtIsNull(request.loginId().trim())
                .filter(this::isActive)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_TARGET_NOT_FOUND));
        return toSearchResponse(target, requireActivePhone(target.getId()));
    }

    /**
     * 대상 사용자에게 연락처 초대를 전송한다.
     *
     * @param senderId 초대를 보내는 사용자 아이디
     * @param request 초대를 받을 사용자 정보를 담은 요청
     * @return 생성된 연락처 초대 정보
     */
    @Transactional
    public ContactInvitationResponse sendInvitation(Long senderId, ContactInvitationCreateRequest request) {
        User sender = requireActiveUser(senderId);
        User receiver = requireTarget(request.receiverId());
        if (senderId.equals(receiver.getId())) {
            throw error(ContactErrorCode.CONTACT_CANNOT_INVITE_SELF);
        }
        if (contactRepository.existsByUser_IdAndContactUser_Id(senderId, receiver.getId())) {
            throw error(ContactErrorCode.CONTACT_ALREADY_CONNECTED);
        }

        ensureNotBlocked(sender, receiver);

        // [검증] 초대 대기 중인지 검증
        boolean pendingInvitationExists = invitationRepository.existsBySender_IdAndReceiver_IdAndStatus(
                senderId,
                receiver.getId(),
                ContactInvitationStatus.PENDING
        );
        if (pendingInvitationExists) {
            throw error(ContactErrorCode.CONTACT_INVITATION_ALREADY_SENT);
        }

        // [검증] 보낸 메일인지 검증
        boolean alreadySent = invitationRepository.existsBySender_IdAndReceiver_IdAndStatus(
                receiver.getId(),
                senderId,
                ContactInvitationStatus.PENDING
        );
        if (alreadySent) {
            throw error(ContactErrorCode.CONTACT_INVITATION_ALREADY_RECEIVED);
        }

        ContactInvitation invitation = invitationRepository.save(ContactInvitation.builder()
                .sender(sender)
                .receiver(receiver)
                .build());

        return toInvitationResponse(invitation, receiver);
    }

    /**
     * 사용자가 받은 대기 중인 연락처 초대 목록을 조회한다.
     *
     * @param userId 초대를 받은 사용자 아이디
     * @return 최신순으로 정렬된 수신 초대 목록
     */
    public List<ContactInvitationResponse> getReceivedInvitations(Long userId) {
        requireActiveUser(userId);
        return invitationRepository.findAllByReceiver_IdAndStatusOrderByCreatedAtDesc(
                        userId, ContactInvitationStatus.PENDING).stream()
                .map(invitation -> toInvitationResponse(invitation, invitation.getSender()))
                .toList();
    }

    /**
     * 사용자가 보낸 대기 중인 연락처 초대 목록을 조회한다.
     *
     * @param userId 초대를 보낸 사용자 아이디
     * @return 최신순으로 정렬된 발신 초대 목록
     */
    public List<ContactInvitationResponse> getSentInvitations(Long userId) {
        requireActiveUser(userId);
        return invitationRepository.findAllBySender_IdAndStatusOrderByCreatedAtDesc(
                        userId, ContactInvitationStatus.PENDING).stream()
                .map(invitation -> toInvitationResponse(invitation, invitation.getReceiver()))
                .toList();
    }

    /**
     * 받은 연락처 초대를 수락하고 양방향 연락처를 생성한다.
     *
     * @param userId 초대를 수락하는 사용자 아이디
     * @param invitationId 수락할 연락처 초대 아이디
     */
    @Transactional
    public void acceptInvitation(Long userId, Long invitationId) {
        User receiver = requireActiveUser(userId);
        ContactInvitation invitation = findInvitationForUpdate(invitationId);
        // [검증] 보낸 사람과 받은 사람을 검증
        if (!invitation.getReceiver().getId().equals(userId)) {
            throw error(ContactErrorCode.CONTACT_INVITATION_NOT_RECEIVER);
        }
        requirePending(invitation);

        User sender = invitation.getSender();
        // [검증] 연결 토글을 켰는지 확인
        if (!isActive(sender)) {
            throw error(ContactErrorCode.CONTACT_TARGET_NOT_FOUND);
        }
        // [검증] 나와 나와 연결된 지인을 검증
        if (contactRepository.existsByUser_IdAndContactUser_Id(userId, sender.getId())) {
            throw error(ContactErrorCode.CONTACT_ALREADY_CONNECTED);
        }
        ensureNotBlocked(receiver, sender);

        contactRepository.save(Contact.builder().user(receiver).contactUser(sender).build());
        contactRepository.save(Contact.builder().user(sender).contactUser(receiver).build());
        invitation.accept(LocalDateTime.now());
    }

    /**
     * 받은 연락처 초대를 거절한다.
     *
     * @param userId 초대를 거절하는 사용자 아이디
     * @param invitationId 거절할 연락처 초대 아이디
     */
    @Transactional
    public void rejectInvitation(Long userId, Long invitationId) {
        requireActiveUser(userId);
        ContactInvitation invitation = findInvitationForUpdate(invitationId);
        if (!invitation.getReceiver().getId().equals(userId)) {
            throw error(ContactErrorCode.CONTACT_INVITATION_NOT_RECEIVER);
        }
        requirePending(invitation);
        invitation.reject(LocalDateTime.now());
    }

    /**
     * 보낸 연락처 초대를 취소한다.
     *
     * @param userId 초대를 취소하는 사용자 아이디
     * @param invitationId 취소할 연락처 초대 아이디
     */
    @Transactional
    public void cancelInvitation(Long userId, Long invitationId) {
        requireActiveUser(userId);
        ContactInvitation invitation = findInvitationForUpdate(invitationId);
        if (!invitation.getSender().getId().equals(userId)) {
            throw error(ContactErrorCode.CONTACT_INVITATION_NOT_SENDER);
        }
        requirePending(invitation);
        invitation.cancel(LocalDateTime.now());
    }

    /**
     * 사용자의 활성 연락처 목록을 조회한다.
     *
     * @param userId 연락처를 조회할 사용자 아이디
     * @return 최신순으로 정렬된 연락처 목록
     */
    public List<ContactResponse> getContacts(Long userId) {
        requireActiveUser(userId);
        return contactRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .filter(contact -> isActive(contact.getContactUser()))
                .map(this::toContactResponse)
                .toList();
    }

    /**
     * 사용자의 연락처 연결을 양방향으로 해제한다.
     *
     * @param userId 연결을 해제하는 사용자 아이디
     * @param contactId 삭제할 연락처 아이디
     */
    @Transactional
    public void disconnect(Long userId, Long contactId) {
        requireActiveUser(userId);
        Contact contact = contactRepository.findByIdAndUser_Id(contactId, userId)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_NOT_FOUND));
        Long contactUserId = contact.getContactUser().getId();
        contactRepository.delete(contact);
        contactRepository.deleteByUser_IdAndContactUser_Id(contactUserId, userId);
    }

    /**
     * 특정 연락처의 실시간 위치 수신 여부를 변경한다.
     *
     * @param userId 설정을 변경하는 사용자 아이디
     * @param contactId 설정을 변경할 연락처 아이디
     * @param request 실시간 위치 수신 여부를 담은 요청
     * @return 변경된 실시간 위치 수신 설정
     */
    @Transactional
    public ReceiveRealtimeResponse updateReceiveRealtime(
            Long userId, Long contactId, ReceiveRealtimeRequest request) {
        requireActiveUser(userId);
        Contact contact = contactRepository.findByIdAndUser_Id(contactId, userId)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_NOT_FOUND));
        contact.updateReceiveRealtime(request.receiveRealtime());
        return new ReceiveRealtimeResponse(contact.getId(), contact.isReceiveRealtime());
    }

    /**
     * 사용자가 차단한 사용자 목록을 조회한다.
     *
     * @param userId 차단 목록을 조회할 사용자 아이디
     * @return 최신순으로 정렬된 차단 사용자 목록
     */
    public List<BlockedUserResponse> getBlockedUsers(Long userId) {
        requireActiveUser(userId);
        return blockedUserRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toBlockedUserResponse)
                .toList();
    }

    /**
     * 대상 사용자를 차단하고 기존 연락처 및 대기 중인 초대를 정리한다.
     *
     * @param userId 차단을 수행하는 사용자 아이디
     * @param request 차단할 사용자 정보를 담은 요청
     * @return 생성된 차단 정보
     */
    @Transactional
    public BlockedUserResponse blockUser(Long userId, BlockedUserCreateRequest request) {
        User user = requireActiveUser(userId);
        User target = requireTarget(request.blockedUserId());
        if (userId.equals(target.getId())) {
            throw error(ContactErrorCode.BLOCK_CANNOT_BLOCK_SELF);
        }

        if (blockedUserRepository.existsByUser_IdAndBlockedUser_Id(userId, target.getId())) {
            throw error(ContactErrorCode.BLOCK_ALREADY_BLOCKED);
        }

        BlockedUser blocked = blockedUserRepository.save(BlockedUser.builder()
                .user(user)
                .blockedUser(target)
                .build());

        contactRepository.deleteByUser_IdAndContactUser_Id(userId, target.getId());
        contactRepository.deleteByUser_IdAndContactUser_Id(target.getId(), userId);
        LocalDateTime now = LocalDateTime.now();
        invitationRepository.findBetweenUsersByStatus(
                        userId, target.getId(), ContactInvitationStatus.PENDING)
                .forEach(invitation -> invitation.cancel(now));
        return toBlockedUserResponse(blocked);
    }

    /**
     * 대상 사용자에 대한 차단을 해제한다.
     *
     * @param userId 차단을 해제하는 사용자 아이디
     * @param blockedUserId 차단을 해제할 사용자 아이디
     */
    @Transactional
    public void unblockUser(Long userId, Long blockedUserId) {
        requireActiveUser(userId);
        BlockedUser blocked = blockedUserRepository.findByUser_IdAndBlockedUser_Id(userId, blockedUserId)
                .orElseThrow(() -> error(ContactErrorCode.BLOCK_NOT_FOUND));
        blockedUserRepository.delete(blocked);
    }

    /**
     * 아이디에 해당하는 활성 사용자를 조회한다.
     *
     * @param userId 조회할 사용자 아이디
     * @return 조회된 활성 사용자
     */
    private User requireActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .filter(this::isActive)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_TARGET_NOT_FOUND));
    }

    /**
     * 연락처 기능의 대상이 될 활성 사용자를 조회한다.
     *
     * @param userId 조회할 대상 사용자 아이디
     * @return 조회된 활성 대상 사용자
     */
    private User requireTarget(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .filter(this::isActive)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_TARGET_NOT_FOUND));
    }

    /**
     * 사용자가 탈퇴하지 않은 활성 상태인지 확인한다.
     *
     * @param user 확인할 사용자
     * @return 활성 사용자이면 {@code true}, 아니면 {@code false}
     */
    private boolean isActive(User user) {
        return user.getDeletedAt() == null && user.getStatus() == UserStatus.ACTIVE;
    }

    /**
     * 사용자의 삭제되지 않은 전화번호 정보를 조회한다.
     *
     * @param userId 전화번호를 조회할 사용자 아이디
     * @return 조회된 전화번호 정보
     */
    private UserPhone requireActivePhone(Long userId) {
        return userPhoneRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_TARGET_NOT_FOUND));
    }

    /**
     * 두 사용자 사이에 어느 방향으로든 차단 관계가 있는지 검증한다.
     *
     * @param requester 차단 여부를 검사할 요청 사용자
     * @param target 요청의 대상 사용자
     */
    private void ensureNotBlocked(User requester, User target) {
        if (blockedUserRepository.existsByUser_IdAndBlockedUser_Id(
                requester.getId(), target.getId())) {
            throw error(ContactErrorCode.BLOCK_TARGET_BLOCKED);
        }
        if (blockedUserRepository.existsByUser_IdAndBlockedUser_Id(
                target.getId(), requester.getId())) {
            throw error(ContactErrorCode.BLOCKED_BY_TARGET);
        }
    }

    /**
     * 연락처 초대를 비관적 쓰기 잠금으로 조회한다.
     *
     * @param invitationId 조회할 연락처 초대 아이디
     * @return 잠금이 적용된 연락처 초대
     */
    private ContactInvitation findInvitationForUpdate(Long invitationId) {
        return invitationRepository.findByIdForUpdate(invitationId)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_INVITATION_NOT_FOUND));
    }

    /**
     * 연락처 초대가 대기 상태인지 검증한다.
     *
     * @param invitation 상태를 확인할 연락처 초대
     */
    private void requirePending(ContactInvitation invitation) {
        switch (invitation.getStatus()) {
            case PENDING -> { }
            case ACCEPTED -> throw error(ContactErrorCode.CONTACT_INVITATION_ALREADY_ACCEPTED);
            case REJECTED -> throw error(ContactErrorCode.CONTACT_INVITATION_ALREADY_REJECTED);
            case CANCELED -> throw error(ContactErrorCode.CONTACT_INVITATION_ALREADY_CANCELED);
        }
    }

    /**
     * 사용자와 전화번호 정보를 사용자 검색 응답으로 변환한다.
     *
     * @param user 검색된 사용자
     * @param phone 사용자의 전화번호 정보
     * @return 사용자 검색 응답
     */
    private UserSearchResponse toSearchResponse(User user, UserPhone phone) {
        String profileImageUrl = profileImageUrlResolver.resolve(user);
        return new UserSearchResponse(
                user.getId(),
                user.getName(),
                user.getLoginId(),
                phoneCryptoService.decrypt(phone.getPhoneEncrypted()),
                profileImageUrl
        );
    }

    /**
     * 연락처 초대와 상대방 정보를 초대 응답으로 변환한다.
     *
     * @param invitation 변환할 연락처 초대
     * @param counterpart 초대 관계의 상대방
     * @return 연락처 초대 응답
     */
    private ContactInvitationResponse toInvitationResponse(ContactInvitation invitation, User counterpart) {
        UserPhone phone = requireActivePhone(counterpart.getId());
        String profileImageUrl = profileImageUrlResolver.resolve(counterpart);
        return new ContactInvitationResponse(invitation.getId(), counterpart.getId(), counterpart.getName(),
                counterpart.getLoginId(), phoneCryptoService.decrypt(phone.getPhoneEncrypted()),
                profileImageUrl, invitation.getStatus(), invitation.getCreatedAt());
    }

    /**
     * 연락처 엔티티를 연락처 응답으로 변환한다.
     *
     * @param contact 변환할 연락처
     * @return 연락처 응답
     */
    private ContactResponse toContactResponse(Contact contact) {
        User counterpart = contact.getContactUser();
        UserPhone phone = requireActivePhone(counterpart.getId());
        String profileImageUrl = profileImageUrlResolver.resolve(counterpart);
        return new ContactResponse(contact.getId(), counterpart.getId(), counterpart.getName(),
                counterpart.getLoginId(), phoneCryptoService.decrypt(phone.getPhoneEncrypted()),
                profileImageUrl, contact.isReceiveRealtime(), contact.getCreatedAt());
    }

    /**
     * 차단 엔티티를 차단 사용자 응답으로 변환한다.
     *
     * @param blocked 변환할 차단 정보
     * @return 차단 사용자 응답
     */
    private BlockedUserResponse toBlockedUserResponse(BlockedUser blocked) {
        User target = blocked.getBlockedUser();
        if (target == null) {
            return new BlockedUserResponse(null, null, null, null, null, blocked.getCreatedAt());
        }
        String phoneNumber = userPhoneRepository.findByUser_IdAndDeletedAtIsNull(target.getId())
                .map(UserPhone::getPhoneEncrypted)
                .map(phoneCryptoService::decrypt)
                .orElse(null);
        String profileImageUrl = profileImageUrlResolver.resolve(target);
        return new BlockedUserResponse(target.getId(), target.getName(), target.getLoginId(), phoneNumber,
                profileImageUrl, blocked.getCreatedAt());
    }

    /**
     * 연락처 오류 코드에 해당하는 비즈니스 예외를 생성한다.
     *
     * @param errorCode 적용할 연락처 오류 코드
     * @return 생성된 비즈니스 예외
     */
    private BusinessException error(ContactErrorCode errorCode) {
        return new BusinessException(errorCode);
    }
}
