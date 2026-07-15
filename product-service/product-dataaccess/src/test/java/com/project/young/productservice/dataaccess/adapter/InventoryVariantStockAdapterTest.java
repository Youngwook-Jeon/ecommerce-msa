package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.application.port.output.InventoryVariantStockPort.VariantStockSnapshot;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.repository.ProductVariantJpaRepository;
import com.project.young.productservice.domain.exception.InventoryDomainException;
import com.project.young.productservice.domain.exception.ProductDomainException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryVariantStockAdapterTest {

    private static final UUID FIRST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private ProductVariantJpaRepository productVariantJpaRepository;
    @Mock
    private EntityManager entityManager;
    @InjectMocks
    private InventoryVariantStockAdapter adapter;

    @Test
    @DisplayName("findOrderedByIds: id를 정렬해 조회하고 재고 및 판매 가능 상태를 매핑한다")
    void findOrderedByIdsSortsIdsAndMapsSnapshots() {
        ProductVariantEntity active = variant(FIRST_ID, 7, ProductStatusEntity.ACTIVE, ProductStatusEntity.ACTIVE);
        ProductVariantEntity discontinuedProduct =
                variant(SECOND_ID, 4, ProductStatusEntity.ACTIVE, ProductStatusEntity.DISCONTINUED);
        when(productVariantJpaRepository.findAllByIdInWithProductOrdered(List.of(FIRST_ID, SECOND_ID)))
                .thenReturn(List.of(active, discontinuedProduct));

        List<VariantStockSnapshot> result = adapter.findOrderedByIds(List.of(
                new ProductVariantId(SECOND_ID),
                new ProductVariantId(FIRST_ID)
        ));

        assertThat(result).containsExactly(
                new VariantStockSnapshot(new ProductVariantId(FIRST_ID), 7, true),
                new VariantStockSnapshot(new ProductVariantId(SECOND_ID), 4, false)
        );
    }

    @Test
    @DisplayName("touchVersions: 정렬된 variant에 force increment lock을 순서대로 적용하고 flush한다")
    void touchVersionsLocksInOrderAndFlushes() {
        ProductVariantEntity first = variant(FIRST_ID, 7, ProductStatusEntity.ACTIVE, ProductStatusEntity.ACTIVE);
        ProductVariantEntity second = variant(SECOND_ID, 4, ProductStatusEntity.ACTIVE, ProductStatusEntity.ACTIVE);
        when(productVariantJpaRepository.findAllByIdInWithProductOrdered(List.of(FIRST_ID, SECOND_ID)))
                .thenReturn(List.of(first, second));

        adapter.touchVersions(List.of(new ProductVariantId(SECOND_ID), new ProductVariantId(FIRST_ID)));

        InOrder inOrder = inOrder(entityManager);
        inOrder.verify(entityManager).lock(first, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        inOrder.verify(entityManager).lock(second, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        inOrder.verify(entityManager).flush();
    }

    @Test
    @DisplayName("touchVersions: 요청한 variant 일부가 없으면 lock을 적용하지 않는다")
    void touchVersionsRejectsMissingVariantBeforeLocking() {
        when(productVariantJpaRepository.findAllByIdInWithProductOrdered(List.of(FIRST_ID, SECOND_ID)))
                .thenReturn(List.of(variant(FIRST_ID, 7, ProductStatusEntity.ACTIVE, ProductStatusEntity.ACTIVE)));

        assertThatThrownBy(() -> adapter.touchVersions(
                List.of(new ProductVariantId(SECOND_ID), new ProductVariantId(FIRST_ID))))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("not found");
        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("touchVersions: 빈 요청이면 repository와 EntityManager를 호출하지 않는다")
    void touchVersionsReturnsImmediatelyForEmptyIds() {
        adapter.touchVersions(List.of());

        verifyNoInteractions(productVariantJpaRepository, entityManager);
    }

    @Test
    @DisplayName("decreaseOnHandForConfirmedHold: 재고를 차감하고 0이면 OUT_OF_STOCK으로 변경한다")
    void decreaseOnHandChangesStockAndStatusThenFlushes() {
        ProductVariantEntity entity =
                variant(FIRST_ID, 2, ProductStatusEntity.ACTIVE, ProductStatusEntity.DISCONTINUED);
        when(productVariantJpaRepository.findById(FIRST_ID)).thenReturn(Optional.of(entity));

        adapter.decreaseOnHandForConfirmedHold(new ProductVariantId(FIRST_ID), 2);

        assertThat(entity.getStockQuantity()).isZero();
        assertThat(entity.getStatus()).isEqualTo(ProductStatusEntity.OUT_OF_STOCK);
        verify(entityManager).flush();
    }

    @Test
    @DisplayName("decreaseOnHandForConfirmedHold: 재고가 부족하면 변경하거나 flush하지 않는다")
    void decreaseOnHandRejectsNegativeStock() {
        ProductVariantEntity entity =
                variant(FIRST_ID, 1, ProductStatusEntity.ACTIVE, ProductStatusEntity.ACTIVE);
        when(productVariantJpaRepository.findById(FIRST_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() ->
                adapter.decreaseOnHandForConfirmedHold(new ProductVariantId(FIRST_ID), 2))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("negative");

        assertThat(entity.getStockQuantity()).isEqualTo(1);
        assertThat(entity.getStatus()).isEqualTo(ProductStatusEntity.ACTIVE);
        verify(entityManager, never()).flush();
    }

    @Test
    @DisplayName("decreaseOnHandForConfirmedHold: 존재하지 않는 variant면 예외를 던진다")
    void decreaseOnHandRejectsMissingVariant() {
        when(productVariantJpaRepository.findById(FIRST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                adapter.decreaseOnHandForConfirmedHold(new ProductVariantId(FIRST_ID), 1))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining(FIRST_ID.toString());
    }

    private static ProductVariantEntity variant(
            UUID id,
            int stock,
            ProductStatusEntity variantStatus,
            ProductStatusEntity productStatus
    ) {
        ProductEntity product = ProductEntity.builder()
                .id(UUID.randomUUID())
                .status(productStatus)
                .build();
        return ProductVariantEntity.builder()
                .id(id)
                .product(product)
                .stockQuantity(stock)
                .status(variantStatus)
                .build();
    }
}
