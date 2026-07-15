package com.project.young.productservice.application.port.output;

import com.project.young.common.domain.valueobject.ProductVariantId;

import java.util.Collection;
import java.util.List;

/**
 * Variant on-hand reads and optimistic-lock touches for soft-hold inventory.
 */
public interface InventoryVariantStockPort {

    record VariantStockSnapshot(
            ProductVariantId variantId,
            int onHand,
            boolean reservable
    ) {
    }

    /**
     * Loads variant stock snapshots. Results are ordered by variant id ascending for stable
     * iteration; locking / concurrency control is handled by {@link #touchVersions} or
     * {@link #decreaseOnHandForConfirmedHold}, not by this method.
     * Missing ids are omitted from the result.
     */
    List<VariantStockSnapshot> findOrderedByIds(Collection<ProductVariantId> variantIds);

    /**
     * Force-increments {@code product_variants.version} in ascending id order so concurrent
     * soft-holds conflict. Call before availability checks when reserving.
     */
    void touchVersions(Collection<ProductVariantId> variantIds);

    /**
     * Commits a soft-hold by decreasing on-hand stock.
     * <p>
     * This is the concurrency gate for confirm: {@code @Version} on the variant row detects
     * concurrent stock mutations. Catalog sellability is intentionally not re-checked —
     * once reserved, a hold remains confirmable even if the product/variant later becomes
     * discontinued or deleted.
     */
    void decreaseOnHandForConfirmedHold(ProductVariantId variantId, int quantity);
}
