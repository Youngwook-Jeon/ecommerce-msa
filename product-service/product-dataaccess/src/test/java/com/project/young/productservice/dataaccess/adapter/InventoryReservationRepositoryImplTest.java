package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CheckoutId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.dataaccess.entity.InventoryReservationEntity;
import com.project.young.productservice.dataaccess.mapper.InventoryReservationDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.InventoryReservationJpaRepository;
import com.project.young.productservice.domain.entity.InventoryReservation;
import com.project.young.productservice.domain.exception.InventoryReservationNotFoundException;
import com.project.young.productservice.domain.valueobject.InventoryReservationId;
import com.project.young.productservice.domain.valueobject.InventoryReservationStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservationRepositoryImplTest {

    @Mock
    private InventoryReservationJpaRepository jpaRepository;
    @Mock
    private InventoryReservationDataAccessMapper mapper;
    @Mock
    private EntityManager entityManager;
    @InjectMocks
    private InventoryReservationRepositoryImpl repository;

    @Test
    @DisplayName("insertAll: domain reservation을 매핑해 일괄 저장한다")
    void insertAllMapsAndSaves() {
        InventoryReservation first = reservation(UUID.randomUUID(), UUID.randomUUID(), 1);
        InventoryReservation second = reservation(UUID.randomUUID(), UUID.randomUUID(), 2);
        InventoryReservationEntity firstEntity = entity(first);
        InventoryReservationEntity secondEntity = entity(second);
        when(mapper.toEntity(first)).thenReturn(firstEntity);
        when(mapper.toEntity(second)).thenReturn(secondEntity);

        repository.insertAll(List.of(first, second));

        verify(jpaRepository).saveAll(List.of(firstEntity, secondEntity));
    }

    @Test
    @DisplayName("insertAll: 빈 컬렉션이면 저장소를 호출하지 않는다")
    void insertAllSkipsEmptyCollection() {
        repository.insertAll(List.of());

        verifyNoInteractions(jpaRepository, mapper);
    }

    @Test
    @DisplayName("update: 기존 entity를 조회해 변경 필드를 매핑한다")
    void updateMapsOntoManagedEntity() {
        InventoryReservation reservation = reservation(UUID.randomUUID(), UUID.randomUUID(), 2);
        InventoryReservationEntity entity = entity(reservation);
        when(jpaRepository.findById(reservation.getId().getValue())).thenReturn(Optional.of(entity));

        repository.update(reservation);

        verify(mapper).updateEntity(reservation, entity);
    }

    @Test
    @DisplayName("update: reservation이 없으면 not found 예외를 던진다")
    void updateRejectsMissingReservation() {
        InventoryReservation reservation = reservation(UUID.randomUUID(), UUID.randomUUID(), 2);
        when(jpaRepository.findById(reservation.getId().getValue())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repository.update(reservation))
                .isInstanceOf(InventoryReservationNotFoundException.class)
                .hasMessageContaining(reservation.getId().getValue().toString());
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("sumActiveQuantityByVariantIds: 집계 결과를 variant별 Map으로 변환한다")
    void sumActiveQuantityByVariantIdsConvertsRowsToMap() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        List<ProductVariantId> ids = List.of(new ProductVariantId(firstId), new ProductVariantId(secondId));
        when(jpaRepository.sumActiveQuantityByVariantIds(List.of(firstId, secondId), now))
                .thenReturn(List.<Object[]>of(
                        new Object[]{firstId, 3L},
                        new Object[]{secondId, 2L}
                ));

        Map<UUID, Integer> result = repository.sumActiveQuantityByVariantIds(ids, now);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(firstId, 3, secondId, 2));
    }

    @Test
    @DisplayName("sumActiveQuantityByVariantIds: 빈 variant 목록이면 쿼리하지 않는다")
    void sumActiveQuantityByVariantIdsSkipsEmptyCollection() {
        Map<UUID, Integer> result = repository.sumActiveQuantityByVariantIds(List.of(), Instant.now());

        assertThat(result).isEmpty();
        verifyNoInteractions(jpaRepository);
    }

    @Test
    @DisplayName("findDueActiveForUpdate: 조회 결과를 domain으로 매핑한다")
    void findDueActiveForUpdateMapsRows() {
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        InventoryReservation domain = reservation(UUID.randomUUID(), UUID.randomUUID(), 1);
        InventoryReservationEntity entity = entity(domain);
        when(jpaRepository.findDueActiveForUpdateSkipLocked(now, 20)).thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(repository.findDueActiveForUpdate(now, 20)).containsExactly(domain);
    }

    @Test
    @DisplayName("findDueActiveForUpdate: limit이 양수가 아니면 쿼리하지 않는다")
    void findDueActiveForUpdateRejectsNonPositiveLimit() {
        assertThatThrownBy(() -> repository.findDueActiveForUpdate(Instant.now(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        verifyNoInteractions(jpaRepository);
    }

    @Test
    @DisplayName("flush: EntityManager에 flush를 위임한다")
    void flushDelegatesToEntityManager() {
        repository.flush();

        verify(entityManager).flush();
    }

    private static InventoryReservation reservation(UUID checkoutId, UUID variantId, int quantity) {
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        return InventoryReservation.reconstitute(
                new InventoryReservationId(UUID.randomUUID()),
                new CheckoutId(checkoutId),
                new ProductVariantId(variantId),
                quantity,
                InventoryReservationStatus.ACTIVE,
                now.plusSeconds(900),
                now,
                now
        );
    }

    private static InventoryReservationEntity entity(InventoryReservation reservation) {
        return InventoryReservationEntity.builder()
                .id(reservation.getId().getValue())
                .checkoutId(reservation.getCheckoutId().getValue())
                .productVariantId(reservation.getProductVariantId().getValue())
                .quantity(reservation.getQuantity())
                .status(reservation.getStatus().name())
                .expiresAt(reservation.getExpiresAt())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }
}
