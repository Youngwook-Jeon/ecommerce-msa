package com.project.young.productservice.application.service;

import com.project.young.productservice.application.port.output.PublicProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadCartCatalogLineView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCartCatalogQueryServiceTest {

    @Mock
    private PublicProductReadRepository publicProductReadRepository;

    private PublicCartCatalogQueryService service;

    @BeforeEach
    void setUp() {
        service = new PublicCartCatalogQueryService(publicProductReadRepository);
    }

    @Test
    @DisplayName("resolveCartLines: variant id 목록으로 카탈로그 라인을 조회한다")
    void resolveCartLines_success() {
        UUID variantId = UUID.randomUUID();
        ReadCartCatalogLineView line = ReadCartCatalogLineView.builder()
                .productId(UUID.randomUUID())
                .productVariantId(variantId)
                .productName("Phone")
                .brand("Brand")
                .sku("SKU-1")
                .imageUrl("https://img")
                .unitPrice(new BigDecimal("100.00"))
                .purchasable(true)
                .stockQuantity(3)
                .variantOptions(List.of())
                .build();

        when(publicProductReadRepository.findCartCatalogLinesByVariantIds(List.of(variantId)))
                .thenReturn(List.of(line));

        List<ReadCartCatalogLineView> result = service.resolveCartLines(List.of(variantId));

        assertThat(result).containsExactly(line);
        verify(publicProductReadRepository).findCartCatalogLinesByVariantIds(List.of(variantId));
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
}
