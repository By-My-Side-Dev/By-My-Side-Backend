package com.gyeote.gyeote_api.domain.contact.repository;

import com.gyeote.gyeote_api.domain.contact.entity.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {

    boolean existsByUser_IdAndBlockedUser_Id(Long userId, Long blockedUserId);

    Optional<BlockedUser> findByUser_IdAndBlockedUser_Id(Long userId, Long blockedUserId);

    List<BlockedUser> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
}
