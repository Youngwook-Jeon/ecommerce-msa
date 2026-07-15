package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.application.port.output.InventoryVariantStockPort;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.repository.ProductVariantJpaRepository;
import com.project.young.productservice.domain.exception.InventoryDomainException;
import com.project.young.productservice.domain.exception.ProductDomainException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
public class InventoryVariantStockAdapter implements InventoryVariantStockPort {

    private final ProductVariantJpaRepository productVariantJpaRepository;
    private final EntityManager entityManager;

    public InventoryVariantStockAdapter(
            ProductVariantJpaRepository productVariantJpaRepository,
            EntityManager entityManager
    ) {
        this.productVariantJpaRepository = productVariantJpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public List<VariantStockSnapshot> findOrderedByIds(Collection<ProductVariantId> variantIds) {
        Objects.requireNonNull(variantIds, "variantIds must not be null");
        if (variantIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = variantIds.stream()
                .map(ProductVariantId::getValue)
                .sorted()
                .toList();
        List<ProductVariantEntity> entities = productVariantJpaRepository.findAllByIdInWithProductOrdered(ids);

        List<VariantStockSnapshot> snapshots = new ArrayList<>(entities.size());
        for (ProductVariantEntity entity : entities) {
            snapshots.add(new VariantStockSnapshot(
                    new ProductVariantId(entity.getId()),
                    entity.getStockQuantity(),
                    isReservable(entity)
            ));
        }
        return List.copyOf(snapshots);
    }

    @Override
    @Transactional
    public void touchVersions(Collection<ProductVariantId> variantIds) {
        Objects.requireNonNull(variantIds, "variantIds must not be null");
        if (variantIds.isEmpty()) {
            return;
        }
        List<UUID> ids = variantIds.stream()
                .map(ProductVariantId::getValue)
                .sorted()
                .toList();
        List<ProductVariantEntity> entities = productVariantJpaRepository.findAllByIdInWithProductOrdered(ids);
        if (entities.size() != ids.size()) {
            throw new InventoryDomainException("One or more product variants were not found for inventory lock.");
        }
        for (ProductVariantEntity entity : entities) {
            entityManager.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        entityManager.flush();
    }

    @Override
    @Transactional
    public void decreaseOnHandForConfirmedHold(ProductVariantId variantId, int quantity) {
        Objects.requireNonNull(variantId, "variantId must not be null");
        if (quantity <= 0) {
            throw new ProductDomainException("Decrease amount must be greater than zero.");
        }
        // Confirm does not re-validate catalog reservability: soft-hold already authorized this qty.
        ProductVariantEntity entity = productVariantJpaRepository.findById(variantId.getValue())
                .orElseThrow(() -> new InventoryDomainException(
                        "Product variant not found: " + variantId.getValue()));

        int next = entity.getStockQuantity() - quantity;
        if (next < 0) {
            throw new ProductDomainException("Stock quantity cannot be negative.");
        }
        entity.setStockQuantity(next);
        if (next == 0 && entity.getStatus() == ProductStatusEntity.ACTIVE) {
            entity.setStatus(ProductStatusEntity.OUT_OF_STOCK);
        }
        // Dirty stock change bumps @Version — concurrency gate for confirm.
        entityManager.flush();
    }

    private static boolean isReservable(ProductVariantEntity entity) {
        if (entity.getStatus() != ProductStatusEntity.ACTIVE) {
            return false;
        }
        ProductStatusEntity productStatus = entity.getProduct().getStatus();
        return productStatus != ProductStatusEntity.DELETED
                && productStatus != ProductStatusEntity.DRAFT
                && productStatus != ProductStatusEntity.DISCONTINUED;
    }
}
