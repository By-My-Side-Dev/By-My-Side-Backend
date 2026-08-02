package com.gyeote.gyeote_api.domain.emergency.repository;

import com.gyeote.gyeote_api.domain.emergency.entity.AlertLocations;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertLocationsRepository extends JpaRepository<AlertLocations, Long> {

    /**
     * 호출 1건의 가장 최근 위치를 조회한다.
     */
    Optional<AlertLocations> findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(Long alertId);

    /**
     * 여러 호출의 최신 위치를 한 번에 조회한다.
     * 같은 시각에 기록된 행이 있을 수 있으므로 호출당 여러 행이 반환될 수 있다.
     */
    @Query("""
            select location
            from AlertLocations location
            where location.emergencyAlert.id in :alertIds
              and location.updatedAt = (
                    select max(latest.updatedAt)
                    from AlertLocations latest
                    where latest.emergencyAlert.id = location.emergencyAlert.id
              )
            order by location.id asc
            """)
    List<AlertLocations> findLatestByAlertIds(@Param("alertIds") Collection<Long> alertIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from AlertLocations location
            where location.deleteAt <= CURRENT_TIMESTAMP
               or location.updatedAt <= :cutoff
            """)
    int deleteExpiredLocations(@Param("cutoff") LocalDateTime cutoff);
}
