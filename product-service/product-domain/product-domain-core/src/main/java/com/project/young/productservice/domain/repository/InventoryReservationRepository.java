package com.project.young.productservice.domain.repository;

import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.domain.entity.InventoryReservation;
import com.project.young.common.domain.valueobject.CheckoutId;
import com.project.young.productservice.domain.valueobject.InventoryReservationId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository {

    void insertAll(Collection<InventoryReservation> reservations);

    void update(InventoryReservation reservation);

    /**
     * Flushes pending persistence changes. Required after ACTIVE→RELEASED updates so a
     * subsequent ACTIVE insert for the same (checkout, variant) does not race Hibernate's
     * insert-before-update flush order against the partial unique index.
     */
    void flush();

    Optional<InventoryReservation> findById(InventoryReservationId id);

    List<InventoryReservation> findByCheckoutId(CheckoutId checkoutId);

    /**
     * Sum of ACTIVE reservation quantities that have not yet expired at {@code now}.
     */
    int sumActiveQuantityByVariantId(ProductVariantId variantId, Instant now);

    /**
     * Batch available-hold sums keyed by variant id. Missing keys mean zero active holds.
     */
    Map<UUID, Integer> sumActiveQuantityByVariantIds(Collection<ProductVariantId> variantIds, Instant now);

    /**
     * ACTIVE rows with {@code expires_at <= now}, locked with {@code FOR UPDATE SKIP LOCKED}
     * for multi-instance expire jobs.
     */
    List<InventoryReservation> findDueActiveForUpdate(Instant now, int limit);
}
