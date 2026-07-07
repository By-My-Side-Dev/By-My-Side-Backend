package com.gyeote.gyeote_api.domain.user.repository;

import com.gyeote.gyeote_api.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginIdAndDeletedAtIsNull(String loginId);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    /**
     * login_id는 탈퇴 계정 포함 전체에서 UNIQUE이므로 soft delete 조건 없이 검사한다.
     */
    boolean existsByLoginId(String loginId);
}
