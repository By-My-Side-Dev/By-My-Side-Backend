package com.gyeote.gyeote_api.domain.device.repository;

import com.gyeote.gyeote_api.domain.device.entity.DeviceToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByUser_Id(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from DeviceToken token where token.user.id = :userId")
    Optional<DeviceToken> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from DeviceToken token where token.tokenHash = :tokenHash")
    Optional<DeviceToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("delete from DeviceToken token where token.user.id = :userId and token.deviceId = :deviceId")
    int deleteCurrentDevice(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    @Modifying(flushAutomatically = true)
    @Query("delete from DeviceToken token where token.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from DeviceToken token where token.tokenHash = :tokenHash")
    int deleteIfCurrentHash(@Param("tokenHash") String tokenHash);
}
