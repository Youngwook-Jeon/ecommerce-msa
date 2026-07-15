package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.InventoryReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InventoryReservationJpaRepository extends JpaRepository<InventoryReservationEntity, UUID> {

    List<InventoryReservationEntity> findByCheckoutIdOrderByProductVariantIdAsc(UUID checkoutId);

    @Query("""
            SELECT COALESCE(SUM(r.quantity), 0)
            FROM InventoryReservationEntity r
            WHERE r.productVariantId = :variantId
              AND r.status = 'ACTIVE'
              AND r.expiresAt > :now
            """)
    long sumActiveQuantityByVariantId(@Param("variantId") UUID variantId, @Param("now") Instant now);

    @Query("""
            SELECT r.productVariantId, COALESCE(SUM(r.quantity), 0)
            FROM InventoryReservationEntity r
            WHERE r.productVariantId IN :variantIds
              AND r.status = 'ACTIVE'
              AND r.expiresAt > :now
            GROUP BY r.productVariantId
            """)
    List<Object[]> sumActiveQuantityByVariantIds(
            @Param("variantIds") Collection<UUID> variantIds,
            @Param("now") Instant now
    );

    @Query(value = """
            SELECT *
            FROM inventory_reservations
            WHERE status = 'ACTIVE'
              AND expires_at <= :now
            ORDER BY expires_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<InventoryReservationEntity> findDueActiveForUpdateSkipLocked(
            @Param("now") Instant now,
            @Param("limit") int limit
    );
}
