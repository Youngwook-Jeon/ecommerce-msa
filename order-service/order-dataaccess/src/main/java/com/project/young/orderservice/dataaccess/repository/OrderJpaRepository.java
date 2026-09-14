package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.entity.OrderEntity;
import com.project.young.orderservice.dataaccess.enums.OrderStatusEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrderEntity o
               set o.status = :targetStatus,
                   o.updatedAt = :updatedAt
             where o.id = :orderId
               and o.userId = :userId
               and o.status = :expectedStatus
            """)
    int updateStatusIfCurrent(
            @Param("orderId") UUID orderId,
            @Param("userId") String userId,
            @Param("expectedStatus") OrderStatusEntity expectedStatus,
            @Param("targetStatus") OrderStatusEntity targetStatus,
            @Param("updatedAt") Instant updatedAt
    );

    @EntityGraph(attributePaths = "lines")
    Optional<OrderEntity> findWithLinesById(UUID id);

    @EntityGraph(attributePaths = "lines")
    Optional<OrderEntity> findWithLinesByIdAndUserId(UUID id, String userId);

    @Query("""
            select o.id as id, o.userId as userId, o.updatedAt as updatedAt
              from OrderEntity o
             where o.status = :status
               and o.updatedAt < :threshold
             order by o.updatedAt asc
            """)
    List<PendingPaymentOrderProjection> findPendingPaymentUpdatedBefore(
            @Param("status") OrderStatusEntity status,
            @Param("threshold") Instant threshold,
            Pageable pageable
    );
}
