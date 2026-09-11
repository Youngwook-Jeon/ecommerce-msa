package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.ProviderSessionRequestEntity;
import com.project.young.paymentservice.application.provider.ProviderSessionRequestStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface ProviderSessionRequestJpaRepository extends JpaRepository<ProviderSessionRequestEntity, UUID> {

    List<ProviderSessionRequestEntity> findTop100ByStatusOrderByCreatedAt(ProviderSessionRequestStatus status);

    List<ProviderSessionRequestEntity> findTop100ByStatusOrderByUpdatedAt(ProviderSessionRequestStatus status);

    @Modifying
    @Query(value = "INSERT INTO provider_session_requests (payment_id,status,created_at,updated_at) VALUES (:id,:status,:now,:now) ON CONFLICT (payment_id) DO NOTHING", nativeQuery = true)
    int enqueue(@Param("id") UUID id, @Param("status") String status, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE provider_session_requests SET status=:targetStatus, processing_started_at=:now, attempts=attempts+1, updated_at=:now WHERE payment_id=:id AND status=:expectedStatus", nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("expectedStatus") String expectedStatus, @Param("targetStatus") String targetStatus, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE provider_session_requests SET status=:status, processing_started_at=NULL, updated_at=:now WHERE payment_id=:id", nativeQuery = true)
    int complete(@Param("id") UUID id, @Param("status") String status, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE provider_session_requests SET status=:status, processing_started_at=NULL, failure_message=:message, updated_at=:now WHERE payment_id=:id", nativeQuery = true)
    int release(@Param("id") UUID id, @Param("status") String status, @Param("message") String message, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE provider_session_requests SET status=:status, processing_started_at=NULL, failure_message=:message, updated_at=:now WHERE payment_id=:id AND status='PENDING'", nativeQuery = true)
    int escalate(@Param("id") UUID id, @Param("status") String status, @Param("message") String message, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE provider_session_requests SET status='PENDING', processing_started_at=NULL, updated_at=:now WHERE status='PROCESSING' AND processing_started_at < :threshold", nativeQuery = true)
    int releaseExpired(@Param("threshold") Instant threshold, @Param("now") Instant now);
}
