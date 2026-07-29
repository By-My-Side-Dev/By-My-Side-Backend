package com.gyeote.gyeote_api.domain.emergency.repository;

import com.gyeote.gyeote_api.domain.emergency.entity.AlertLocations;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AlertLocationsRepository extends JpaRepository<AlertLocations, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from AlertLocations location
            where location.deleteAt <= CURRENT_TIMESTAMP
               or location.updatedAt <= :cutoff
            """)
    int deleteExpiredLocations(@Param("cutoff") LocalDateTime cutoff);
}
