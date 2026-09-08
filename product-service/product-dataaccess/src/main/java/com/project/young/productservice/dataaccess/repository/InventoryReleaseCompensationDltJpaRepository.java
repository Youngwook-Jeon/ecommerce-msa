package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.InventoryReleaseCompensationDltEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface InventoryReleaseCompensationDltJpaRepository
        extends JpaRepository<InventoryReleaseCompensationDltEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO inventory_release_compensation_dlts (
                compensation_event_id, order_id, source_topic, dlt_topic, source_partition, source_offset,
                failure_exception_class, failure_message, created_at
            ) VALUES (
                :compensationEventId, :orderId, :sourceTopic, :dltTopic, :sourcePartition, :sourceOffset,
                :failureExceptionClass, :failureMessage, :createdAt
            ) ON CONFLICT (compensation_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("compensationEventId") UUID compensationEventId,
            @Param("orderId") UUID orderId,
            @Param("sourceTopic") String sourceTopic,
            @Param("dltTopic") String dltTopic,
            @Param("sourcePartition") Integer sourcePartition,
            @Param("sourceOffset") Long sourceOffset,
            @Param("failureExceptionClass") String failureExceptionClass,
            @Param("failureMessage") String failureMessage,
            @Param("createdAt") Instant createdAt
    );
}
