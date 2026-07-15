package com.project.young.productservice.application.service;

import com.project.young.productservice.application.port.output.PublicProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadCartCatalogLineView;
import com.project.young.productservice.domain.repository.InventoryReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCartCatalogQueryServiceTest {

    @Mock
    private PublicProductReadRepository publicProductReadRepository;

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

    private PublicCartCatalogQueryService service;

    @BeforeEach
    void setUp() {
        service = new PublicCartCatalogQueryService(
                publicProductReadRepository,
                inventoryReservationRepository
        );
    }

    @Test
    @DisplayName("resolveCartLines: null 또는 빈 목록이면 빈 결과를 반환한다")
    void resolveCartLines_nullOrEmpty_returnsEmpty() {
        assertThat(service.resolveCartLines(null)).isEmpty();
        assertThat(service.resolveCartLines(List.of())).isEmpty();
        verifyNoInteractions(publicProductReadRepository, inventoryReservationRepository);
    }

    @Test
    @DisplayName("resolveCartLines: null variant id는 거부한다")
    void resolveCartLines_nullVariantId_throws() {
        assertThatThrownBy(() -> service.resolveCartLines(Collections.singletonList(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("resolveCartLines: 중복 variant id는 distinct 후 조회한다")
    void resolveCartLines_deduplicatesVariantIds() {
        UUID variantId = UUID.randomUUID();
        ReadCartCatalogLineView line = line(variantId, 3);

        when(publicProductReadRepository.findCartCatalogLinesByVariantIds(List.of(variantId)))
                .thenReturn(List.of(line));
        when(inventoryReservationRepository.sumActiveQuantityByVariantIds(any(), any(Instant.class)))
                .thenReturn(Map.of());

        service.resolveCartLines(List.of(variantId, variantId));

        verify(publicProductReadRepository).findCartCatalogLinesByVariantIds(List.of(variantId));
        verify(inventoryReservationRepository).sumActiveQuantityByVariantIds(any(), any(Instant.class));
    }

    @Test
    @DisplayName("resolveCartLines: active hold가 없으면 원본 line을 그대로 반환한다")
    void resolveCartLines_noActiveHolds_returnsOriginalLine() {
        UUID variantId = UUID.randomUUID();
        ReadCartCatalogLineView line = line(variantId, 5);

        when(publicProductReadRepository.findCartCatalogLinesByVariantIds(List.of(variantId)))
                .thenReturn(List.of(line));
        when(inventoryReservationRepository.sumActiveQuantityByVariantIds(any(), any(Instant.class)))
                .thenReturn(Map.of());

        List<ReadCartCatalogLineView> result = service.resolveCartLines(List.of(variantId));

        assertThat(result).containsExactly(line);
    }

    @Test
    @DisplayName("resolveCartLines: variant id 목록으로 카탈로그 라인을 조회한다")
    void resolveCartLines_success() {
        UUID variantId = UUID.randomUUID();
        ReadCartCatalogLineView line = line(variantId, 3);

        when(publicProductReadRepository.findCartCatalogLinesByVariantIds(List.of(variantId)))
                .thenReturn(List.of(line));
        when(inventoryReservationRepository.sumActiveQuantityByVariantIds(any(), any(Instant.class)))
                .thenReturn(Map.of());

        List<ReadCartCatalogLineView> result = service.resolveCartLines(List.of(variantId));

        assertThat(result).containsExactly(line);
        verify(publicProductReadRepository).findCartCatalogLinesByVariantIds(List.of(variantId));
    }

    @Test
    @DisplayName("resolveCartLines: active soft-hold를 빼서 available stock을 반환한다")
    void resolveCartLines_subtractsActiveHolds() {
        UUID variantId = UUID.randomUUID();
        ReadCartCatalogLineView line = line(variantId, 5);

        when(publicProductReadRepository.findCartCatalogLinesByVariantIds(List.of(variantId)))
                .thenReturn(List.of(line));
        when(inventoryReservationRepository.sumActiveQuantityByVariantIds(any(), any(Instant.class)))
                .thenReturn(Map.of(variantId, 2));

        List<ReadCartCatalogLineView> result = service.resolveCartLines(List.of(variantId));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().stockQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("resolveCartLines: 50개 초과 variant id는 거부한다")
    void resolveCartLines_tooManyIds_throws() {
        List<UUID> ids = java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> UUID.randomUUID())
                .toList();

        assertThatThrownBy(() -> service.resolveCartLines(ids))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    private static ReadCartCatalogLineView line(UUID variantId, int stockQuantity) {
        return ReadCartCatalogLineView.builder()
                .productId(UUID.randomUUID())
                .productVariantId(variantId)
                .productName("Phone")
                .brand("Brand")
                .sku("SKU-1")
                .imageUrl("https://img")
                .unitPrice(new BigDecimal("100.00"))
                .purchasable(true)
                .stockQuantity(stockQuantity)
                .variantOptions(List.of())
                .build();
    }
}
