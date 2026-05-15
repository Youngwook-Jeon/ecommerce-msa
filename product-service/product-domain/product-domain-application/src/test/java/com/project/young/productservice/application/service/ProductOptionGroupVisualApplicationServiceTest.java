package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.command.UpdateProductOptionGroupVisualCommand;
import com.project.young.productservice.application.dto.result.UpdateProductOptionGroupVisualResult;
import com.project.young.productservice.application.port.output.ProductOptionGroupVisualPort;
import com.project.young.productservice.application.port.output.VariantMainImageSyncPort;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.ProductRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductOptionGroupVisualApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionGroupVisualPort productOptionGroupVisualPort;

    @Mock
    private VariantMainImageSyncPort variantMainImageSyncPort;

    @InjectMocks
    private ProductOptionGroupVisualApplicationService service;

    @Test
    @DisplayName("command가 null이면 IllegalArgumentException을 던진다")
    void updateVisualFlag_throwsWhenCommandNull() {
        UUID productId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();

        assertThatThrownBy(() -> service.updateVisualFlag(productId, pogId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");

        verify(productRepository, never()).findById(any());
    }

    @Test
    @DisplayName("상품이 없으면 ProductNotFoundException을 던진다")
    void updateVisualFlag_throwsWhenProductNotFound() {
        UUID productId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();
        when(productRepository.findById(any(ProductId.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateVisualFlag(
                productId,
                pogId,
                UpdateProductOptionGroupVisualCommand.builder().drivesVariantImages(true).build()
        )).isInstanceOf(ProductNotFoundException.class);

        verify(productOptionGroupVisualPort, never()).setDrivesVariantImages(any(), any(), eq(true));
    }

    @Test
    @DisplayName("삭제된 상품이면 ProductDomainException을 던진다")
    void updateVisualFlag_throwsWhenProductDeleted() {
        UUID productId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();
        Product product = reconstituteProduct(productId, ProductStatus.DELETED);
        when(productRepository.findById(any(ProductId.class))).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.updateVisualFlag(
                productId,
                pogId,
                UpdateProductOptionGroupVisualCommand.builder().drivesVariantImages(true).build()
        )).isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("deleted");

        verify(productOptionGroupVisualPort, never()).setDrivesVariantImages(any(), any(), eq(true));
    }

    @Test
    @DisplayName("visual 플래그를 갱신하고 전체 variant 이미지를 동기화한다")
    void updateVisualFlag_setsFlagAndSyncsVariants() {
        UUID productId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();
        Product product = reconstituteProduct(productId, ProductStatus.DRAFT);
        when(productRepository.findById(any(ProductId.class))).thenReturn(Optional.of(product));

        UpdateProductOptionGroupVisualResult result = service.updateVisualFlag(
                productId,
                pogId,
                UpdateProductOptionGroupVisualCommand.builder().drivesVariantImages(true).build()
        );

        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.productOptionGroupId()).isEqualTo(pogId);
        assertThat(result.drivesVariantImages()).isTrue();

        verify(productOptionGroupVisualPort).setDrivesVariantImages(productId, pogId, true);
        verify(variantMainImageSyncPort).syncAllForProduct(productId);
    }

    @Test
    @DisplayName("drivesVariantImages=false로 visual 플래그를 해제할 수 있다")
    void updateVisualFlag_canClearVisualFlag() {
        UUID productId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();
        Product product = reconstituteProduct(productId, ProductStatus.DRAFT);
        when(productRepository.findById(any(ProductId.class))).thenReturn(Optional.of(product));

        UpdateProductOptionGroupVisualResult result = service.updateVisualFlag(
                productId,
                pogId,
                UpdateProductOptionGroupVisualCommand.builder().drivesVariantImages(false).build()
        );

        assertThat(result.drivesVariantImages()).isFalse();
        verify(productOptionGroupVisualPort).setDrivesVariantImages(productId, pogId, false);
        verify(variantMainImageSyncPort).syncAllForProduct(productId);
    }

    private static Product reconstituteProduct(UUID productId, ProductStatus status) {
        return Product.reconstitute(
                new ProductId(productId),
                null,
                "테스트 상품",
                "테스트 상품 설명입니다. 20자 이상입니다.",
                new Money(new BigDecimal("10000")),
                status,
                ConditionType.NEW,
                "브랜드A",
                "https://example.com/main.jpg",
                List.of(),
                List.of()
        );
    }
}
