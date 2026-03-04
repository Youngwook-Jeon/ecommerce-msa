package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.ProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductQueryServiceTest {

    @Mock
    private ProductReadRepository productReadRepository;

    @InjectMocks
    private ProductQueryService productQueryService;

    @Test
    @DisplayName("getVisibleProductsByCategory: repository 결과를 그대로 반환한다")
    void getVisibleProductsByCategory_ReturnsRepositoryResult() {
        // Given
        CategoryId categoryId = new CategoryId(1L);
        List<ReadProductView> views = List.of(
                ReadProductView.builder()
                        .id(UUID.randomUUID())
                        .categoryId(1L)
                        .name("상품1")
                        .description("설명1")
                        .brand("브랜드1")
                        .mainImageUrl("url1")
                        .basePrice(new BigDecimal("1000"))
                        .status(ProductStatus.ACTIVE)
                        .conditionType(ConditionType.NEW)
                        .build()
        );

        when(productReadRepository.findVisibleByCategoryId(categoryId)).thenReturn(views);

        // When
        List<ReadProductView> result = productQueryService.getVisibleProductsByCategory(categoryId);

        // Then
        assertThat(result).isSameAs(views);
        verify(productReadRepository).findVisibleByCategoryId(categoryId);
        verifyNoMoreInteractions(productReadRepository);
    }

    @Test
    @DisplayName("getVisibleProductDetail: 존재하면 조회 결과를 반환")
    void getVisibleProductDetail_Success() {
        // Given
        UUID rawId = UUID.randomUUID();
        ProductId productId = new ProductId(rawId);

        ReadProductView view = ReadProductView.builder()
                .id(rawId)
                .categoryId(1L)
                .name("상품")
                .description("설명")
                .brand("브랜드")
                .mainImageUrl("url")
                .basePrice(new BigDecimal("1000"))
                .status(ProductStatus.ACTIVE)
                .conditionType(ConditionType.NEW)
                .build();

        when(productReadRepository.findVisibleById(productId)).thenReturn(Optional.of(view));

        // When
        ReadProductView result = productQueryService.getVisibleProductDetail(productId);

        // Then
        assertThat(result).isSameAs(view);
        verify(productReadRepository).findVisibleById(productId);
    }

    @Test
    @DisplayName("getVisibleProductDetail: 존재하지 않거나 visible이 아니면 ProductNotFoundException")
    void getVisibleProductDetail_NotFound_ThrowsException() {
        // Given
        ProductId productId = new ProductId(UUID.randomUUID());
        when(productReadRepository.findVisibleById(productId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productQueryService.getVisibleProductDetail(productId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Product not found or not visible");

        verify(productReadRepository).findVisibleById(productId);
    }
}