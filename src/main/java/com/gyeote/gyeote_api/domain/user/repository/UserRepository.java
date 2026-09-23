package com.gyeote.gyeote_api.domain.user.repository;

import com.gyeote.gyeote_api.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findUserById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Optional<User> findByLoginIdAndDeletedAtIsNull(String loginId);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    Optional<User> findByProviderAndProviderUid(String provider, String providerUid);

    boolean existsByLoginId(String loginId);
}
