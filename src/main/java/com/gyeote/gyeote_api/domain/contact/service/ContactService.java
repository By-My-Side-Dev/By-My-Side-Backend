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

    public UserSearchResponse searchUser(Long userId, UserSearchRequest request) {
        requireActiveUser(userId);
        User target = userRepository.findByLoginIdAndDeletedAtIsNull(request.loginId().trim())
                .filter(this::isActive)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_TARGET_NOT_FOUND));
        return toSearchResponse(target, requireActivePhone(target.getId()));
    }

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

        if (invitationRepository.existsBySender_IdAndReceiver_IdAndStatus(
                senderId, receiver.getId(), ContactInvitationStatus.PENDING)) {
            throw error(ContactErrorCode.CONTACT_INVITATION_ALREADY_SENT);
        }
        if (invitationRepository.existsBySender_IdAndReceiver_IdAndStatus(
                receiver.getId(), senderId, ContactInvitationStatus.PENDING)) {
            throw error(ContactErrorCode.CONTACT_INVITATION_ALREADY_RECEIVED);
        }

        ContactInvitation invitation = invitationRepository.save(ContactInvitation.builder()
                .sender(sender)
                .receiver(receiver)
                .build());
        return toInvitationResponse(invitation, receiver);
    }

    public List<ContactInvitationResponse> getReceivedInvitations(Long userId) {
        requireActiveUser(userId);
        return invitationRepository.findAllByReceiver_IdAndStatusOrderByCreatedAtDesc(
                        userId, ContactInvitationStatus.PENDING).stream()
                .map(invitation -> toInvitationResponse(invitation, invitation.getSender()))
                .toList();
    }

    public List<ContactInvitationResponse> getSentInvitations(Long userId) {
        requireActiveUser(userId);
        return invitationRepository.findAllBySender_IdAndStatusOrderByCreatedAtDesc(
                        userId, ContactInvitationStatus.PENDING).stream()
                .map(invitation -> toInvitationResponse(invitation, invitation.getReceiver()))
                .toList();
    }

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

    public List<ContactResponse> getContacts(Long userId) {
        requireActiveUser(userId);
        return contactRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .filter(contact -> isActive(contact.getContactUser()))
                .map(this::toContactResponse)
                .toList();
    }

    @Transactional
    public void disconnect(Long userId, Long contactId) {
        requireActiveUser(userId);
        Contact contact = contactRepository.findByIdAndUser_Id(contactId, userId)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_NOT_FOUND));
        Long contactUserId = contact.getContactUser().getId();
        contactRepository.delete(contact);
        contactRepository.deleteByUser_IdAndContactUser_Id(contactUserId, userId);
    }

    @Transactional
    public ReceiveRealtimeResponse updateReceiveRealtime(
            Long userId, Long contactId, ReceiveRealtimeRequest request) {
        requireActiveUser(userId);
        Contact contact = contactRepository.findByIdAndUser_Id(contactId, userId)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_NOT_FOUND));
        contact.updateReceiveRealtime(request.receiveRealtime());
        return new ReceiveRealtimeResponse(contact.getId(), contact.isReceiveRealtime());
    }

    public List<BlockedUserResponse> getBlockedUsers(Long userId) {
        requireActiveUser(userId);
        return blockedUserRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toBlockedUserResponse)
                .toList();
    }

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

    @Transactional
    public void unblockUser(Long userId, Long blockedUserId) {
        requireActiveUser(userId);
        BlockedUser blocked = blockedUserRepository.findByUser_IdAndBlockedUser_Id(userId, blockedUserId)
                .orElseThrow(() -> error(ContactErrorCode.BLOCK_NOT_FOUND));
        blockedUserRepository.delete(blocked);
    }

    private User requireActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .filter(this::isActive)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_TARGET_NOT_FOUND));
    }

    private User requireTarget(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .filter(this::isActive)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_TARGET_NOT_FOUND));
    }

    private boolean isActive(User user) {
        return user.getDeletedAt() == null && user.getStatus() == UserStatus.ACTIVE;
    }

    private UserPhone requireActivePhone(Long userId) {
        return userPhoneRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_TARGET_NOT_FOUND));
    }

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

    private ContactInvitation findInvitationForUpdate(Long invitationId) {
        return invitationRepository.findByIdForUpdate(invitationId)
                .orElseThrow(() -> error(ContactErrorCode.CONTACT_INVITATION_NOT_FOUND));
    }

    private void requirePending(ContactInvitation invitation) {
        switch (invitation.getStatus()) {
            case PENDING -> { }
            case ACCEPTED -> throw error(ContactErrorCode.CONTACT_INVITATION_ALREADY_ACCEPTED);
            case REJECTED -> throw error(ContactErrorCode.CONTACT_INVITATION_ALREADY_REJECTED);
            case CANCELED -> throw error(ContactErrorCode.CONTACT_INVITATION_ALREADY_CANCELED);
        }
    }

    private UserSearchResponse toSearchResponse(User user, UserPhone phone) {
        return new UserSearchResponse(user.getId(), user.getName(), user.getLoginId(),
                phoneCryptoService.decrypt(phone.getPhoneEncrypted()), user.getProfileImageUrl());
    }

    private ContactInvitationResponse toInvitationResponse(ContactInvitation invitation, User counterpart) {
        UserPhone phone = requireActivePhone(counterpart.getId());
        return new ContactInvitationResponse(invitation.getId(), counterpart.getId(), counterpart.getName(),
                counterpart.getLoginId(), phoneCryptoService.decrypt(phone.getPhoneEncrypted()),
                counterpart.getProfileImageUrl(), invitation.getStatus(), invitation.getCreatedAt());
    }

    private ContactResponse toContactResponse(Contact contact) {
        User counterpart = contact.getContactUser();
        UserPhone phone = requireActivePhone(counterpart.getId());
        return new ContactResponse(contact.getId(), counterpart.getId(), counterpart.getName(),
                counterpart.getLoginId(), phoneCryptoService.decrypt(phone.getPhoneEncrypted()),
                counterpart.getProfileImageUrl(), contact.isReceiveRealtime(), contact.getCreatedAt());
    }

    private BlockedUserResponse toBlockedUserResponse(BlockedUser blocked) {
        User target = blocked.getBlockedUser();
        if (target == null) {
            return new BlockedUserResponse(null, null, null, null, null, blocked.getCreatedAt());
        }
        String phoneNumber = userPhoneRepository.findByUser_IdAndDeletedAtIsNull(target.getId())
                .map(UserPhone::getPhoneEncrypted)
                .map(phoneCryptoService::decrypt)
                .orElse(null);
        return new BlockedUserResponse(target.getId(), target.getName(), target.getLoginId(), phoneNumber,
                target.getProfileImageUrl(), blocked.getCreatedAt());
    }

    private BusinessException error(ContactErrorCode errorCode) {
        return new BusinessException(errorCode);
    }
}
