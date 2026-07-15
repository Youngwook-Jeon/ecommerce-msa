package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CheckoutId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.dataaccess.entity.InventoryReservationEntity;
import com.project.young.productservice.dataaccess.mapper.InventoryReservationDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.InventoryReservationJpaRepository;
import com.project.young.productservice.domain.entity.InventoryReservation;
import com.project.young.productservice.domain.exception.InventoryReservationNotFoundException;
import com.project.young.productservice.domain.repository.InventoryReservationRepository;
import com.project.young.productservice.domain.valueobject.InventoryReservationId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class InventoryReservationRepositoryImpl implements InventoryReservationRepository {

    private final InventoryReservationJpaRepository jpaRepository;
    private final InventoryReservationDataAccessMapper mapper;
    private final EntityManager entityManager;

    public InventoryReservationRepositoryImpl(
            InventoryReservationJpaRepository jpaRepository,
            InventoryReservationDataAccessMapper mapper,
            EntityManager entityManager
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void insertAll(Collection<InventoryReservation> reservations) {
        Objects.requireNonNull(reservations, "reservations must not be null");
        if (reservations.isEmpty()) {
            return;
        }
        jpaRepository.saveAll(reservations.stream().map(mapper::toEntity).toList());
    }

    @Override
    @Transactional
    public void update(InventoryReservation reservation) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        InventoryReservationEntity entity = jpaRepository.findById(reservation.getId().getValue())
                .orElseThrow(() -> new InventoryReservationNotFoundException(
                        "Inventory reservation not found: " + reservation.getId().getValue()));
        mapper.updateEntity(reservation, entity);
    }

    @Override
    @Transactional
    public void flush() {
        entityManager.flush();
    }

    @Override
    public Optional<InventoryReservation> findById(InventoryReservationId id) {
        Objects.requireNonNull(id, "id must not be null");
        return jpaRepository.findById(id.getValue()).map(mapper::toDomain);
    }

    @Override
    public List<InventoryReservation> findByCheckoutId(CheckoutId checkoutId) {
        Objects.requireNonNull(checkoutId, "checkoutId must not be null");
        return jpaRepository.findByCheckoutIdOrderByProductVariantIdAsc(checkoutId.getValue()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public int sumActiveQuantityByVariantId(ProductVariantId variantId, Instant now) {
        Objects.requireNonNull(variantId, "variantId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return Math.toIntExact(jpaRepository.sumActiveQuantityByVariantId(variantId.getValue(), now));
    }

    @Override
    public Map<UUID, Integer> sumActiveQuantityByVariantIds(
            Collection<ProductVariantId> variantIds,
            Instant now
    ) {
        Objects.requireNonNull(variantIds, "variantIds must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = variantIds.stream().map(ProductVariantId::getValue).toList();
        Map<UUID, Integer> result = new HashMap<>();
        for (Object[] row : jpaRepository.sumActiveQuantityByVariantIds(ids, now)) {
            result.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }

    @Override
    @Transactional
    public List<InventoryReservation> findDueActiveForUpdate(Instant now, int limit) {
        Objects.requireNonNull(now, "now must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jpaRepository.findDueActiveForUpdateSkipLocked(now, limit).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
