package com.gyeote.gyeote_api.domain.user.repository;

import com.gyeote.gyeote_api.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginIdAndDeletedAtIsNull(String loginId);
}
