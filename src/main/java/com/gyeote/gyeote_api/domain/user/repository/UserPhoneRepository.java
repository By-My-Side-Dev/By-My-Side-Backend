package com.gyeote.gyeote_api.domain.user.repository;

import com.gyeote.gyeote_api.domain.user.entity.UserPhone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPhoneRepository extends JpaRepository<UserPhone, Long> {

    Optional<UserPhone> findByActivePhoneHash(String activePhoneHash);
}
