package com.gyeote.gyeote_api.domain.contact.repository;

import com.gyeote.gyeote_api.domain.contact.entity.ContactInvitation;
import com.gyeote.gyeote_api.domain.contact.entity.ContactInvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContactInvitationRepository extends JpaRepository<ContactInvitation, Long> {

    boolean existsBySender_IdAndReceiver_IdAndStatus(Long senderId, Long receiverId, ContactInvitationStatus status);

    List<ContactInvitation> findAllByReceiver_IdAndStatusOrderByCreatedAtDesc(Long receiverId, ContactInvitationStatus status);

    List<ContactInvitation> findAllBySender_IdAndStatusOrderByCreatedAtDesc(Long senderId, ContactInvitationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ContactInvitation i where i.id = :id")
    Optional<ContactInvitation> findByIdForUpdate(@Param("id") Long id);

    @Query("select i from ContactInvitation i where i.status = :status and " +
            "((i.sender.id = :firstId and i.receiver.id = :secondId) or " +
            "(i.sender.id = :secondId and i.receiver.id = :firstId))")
    List<ContactInvitation> findBetweenUsersByStatus(
            @Param("firstId") Long firstId,
            @Param("secondId") Long secondId,
            @Param("status") ContactInvitationStatus status);
}
