package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.InventoryReleaseCompensationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface InventoryReleaseCompensationJpaRepository
        extends JpaRepository<InventoryReleaseCompensationEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO inventory_release_compensations (
                compensation_event_id, order_id, processed_at
            ) VALUES (:compensationEventId, :orderId, :processedAt)
            ON CONFLICT (compensation_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("compensationEventId") UUID compensationEventId,
            @Param("orderId") UUID orderId,
            @Param("processedAt") Instant processedAt
    );
}
