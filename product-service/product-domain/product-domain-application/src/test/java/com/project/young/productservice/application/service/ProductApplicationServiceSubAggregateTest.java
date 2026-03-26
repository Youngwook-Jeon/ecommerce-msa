package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.*;
import com.project.young.productservice.application.dto.command.*;
import com.project.young.productservice.application.dto.result.*;
import com.project.young.productservice.application.mapper.ProductDataMapper;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.entity.ProductOptionGroup;
import com.project.young.productservice.domain.entity.ProductOptionValue;
import com.project.young.productservice.domain.entity.ProductVariant;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.domain.service.ProductDomainService;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceSubAggregateTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductDomainService productDomainService;

    @Spy
    private ProductDataMapper productDataMapper;

    @Mock
    private IdGenerator idGenerator;

    @InjectMocks
    private ProductApplicationService productApplicationService;

    private Product createBaseProduct(UUID productId) {
        return Product.reconstitute(
                new ProductId(productId),
                null,
                "상품",
                "상품 설명은 20자 이상으로 충분히 길어야 합니다.",
                new Money(new BigDecimal("10000")),
                ProductStatus.ACTIVE,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                List.of(),
                List.of()
        );
    }

    @Nested
    @DisplayName("addProductOptionGroup")
    class AddProductOptionGroupTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void invalidRequest_Throws() {
            assertThatThrownBy(() -> productApplicationService.addProductOptionGroup(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid add product option group request");
        }

        @Test
        @DisplayName("대상이 없으면 ProductNotFoundException")
        void notFound_Throws() {
            UUID productId = UUID.randomUUID();
            AddProductOptionGroupCommand command = AddProductOptionGroupCommand.builder()
                    .optionGroupId(UUID.randomUUID())
                    .stepOrder(1)
                    .required(true)
                    .optionValues(List.of(
                            AddProductOptionValueCommand.builder()
                                    .optionValueId(UUID.randomUUID())
                                    .priceDelta(BigDecimal.ZERO)
                                    .isDefault(false)
                                    .isActive(true)
                                    .build()
                    ))
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productApplicationService.addProductOptionGroup(productId, command))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("Product with id");
        }

        @Test
        @DisplayName("정상 추가 시 ProductOptionGroup이 저장된다")
        void success() {
            UUID productId = UUID.randomUUID();
            Product product = createBaseProduct(productId);

            UUID optionGroupId = UUID.randomUUID();
            UUID optionValueId = UUID.randomUUID();

            AddProductOptionGroupCommand command = AddProductOptionGroupCommand.builder()
                    .optionGroupId(optionGroupId)
                    .stepOrder(1)
                    .required(true)
                    .optionValues(List.of(
                            AddProductOptionValueCommand.builder()
                                    .optionValueId(optionValueId)
                                    .priceDelta(new BigDecimal("0"))
                                    .isDefault(true)
                                    .isActive(true)
                                    .build()
                    ))
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);
            UUID generatedProductOptionGroupId = UUID.randomUUID();
            UUID generatedProductOptionValueId = UUID.randomUUID();
            when(idGenerator.generateId()).thenReturn(generatedProductOptionGroupId, generatedProductOptionValueId);

            AddProductOptionGroupResult result = productApplicationService.addProductOptionGroup(productId, command);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.optionGroupId()).isEqualTo(optionGroupId);
            assertThat(result.productOptionGroupId()).isEqualTo(generatedProductOptionGroupId);
            assertThat(result.stepOrder()).isEqualTo(1);
            assertThat(result.optionValueCount()).isEqualTo(1);
            assertThat(product.getOptionGroups()).hasSize(1);

            verify(productRepository).save(product);
        }
    }

    @Nested
    @DisplayName("addProductVariant")
    class AddProductVariantTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void invalidRequest_Throws() {
            assertThatThrownBy(() -> productApplicationService.addProductVariant(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid add product variant request");
        }

        @Test
        @DisplayName("SKU가 5회 이상 중복이면 ProductDomainException")
        void duplicateSku_Throws() {
            UUID productId = UUID.randomUUID();
            UUID productVariantId = UUID.randomUUID();
            Product product = createBaseProduct(productId);

            AddProductVariantCommand command = AddProductVariantCommand.builder()
                    .stockQuantity(5)
                    .selectedProductOptionValueIds(Set.of(UUID.randomUUID()))
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            when(idGenerator.generateId()).thenReturn(productVariantId);
            doThrow(new ProductDomainException("Global SKU uniqueness violation"))
                    .when(productDomainService).validateGlobalSkuUniqueness(anyString());

            assertThatThrownBy(() -> productApplicationService.addProductVariant(productId, command))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Failed to generate unique SKU after");

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("정상 추가 시 Variant가 계산가와 함께 저장된다")
        void success() {
            UUID productId = UUID.randomUUID();
            Product product = createBaseProduct(productId);

            UUID globalOptionGroupId = UUID.randomUUID();
            UUID globalOptionValueId = UUID.randomUUID();
            UUID productOptionValueId = UUID.randomUUID();

            ProductOptionValue pov = ProductOptionValue.reconstitute(
                    new ProductOptionValueId(productOptionValueId),
                    new OptionValueId(globalOptionValueId),
                    new Money(new BigDecimal("1000")),
                    true,
                    true
            );

            ProductOptionGroup pog = ProductOptionGroup.reconstitute(
                    new ProductOptionGroupId(UUID.randomUUID()),
                    new OptionGroupId(globalOptionGroupId),
                    1,
                    true,
                    List.of(pov)
            );

            product.addOptionGroup(pog);

            AddProductVariantCommand command = AddProductVariantCommand.builder()
                    .stockQuantity(3)
                    .selectedProductOptionValueIds(Set.of(productOptionValueId))
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);
            UUID generatedVariantId = UUID.randomUUID();
            when(idGenerator.generateId()).thenReturn(generatedVariantId);

            AddProductVariantResult result = productApplicationService.addProductVariant(productId, command);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.productVariantId()).isEqualTo(generatedVariantId);
            assertThat(result.sku()).startsWith("PRD-");
            assertThat(result.stockQuantity()).isEqualTo(3);
            assertThat(result.calculatedPrice()).isEqualByComparingTo(new BigDecimal("11000.00"));
            assertThat(product.getVariants()).hasSize(1);

            verify(productDomainService).validateGlobalSkuUniqueness(result.sku());
            verify(productRepository).save(product);
        }

        @Test
        @DisplayName("SKU 충돌 시 최대 5회 내에서 재시도 후 성공")
        void retriesOnSkuCollision_ThenSucceeds() {
            UUID productId = UUID.randomUUID();
            Product product = createBaseProduct(productId);

            UUID globalOptionGroupId = UUID.randomUUID();
            UUID globalOptionValueId = UUID.randomUUID();
            UUID productOptionValueId = UUID.randomUUID();

            ProductOptionValue pov = ProductOptionValue.reconstitute(
                    new ProductOptionValueId(productOptionValueId),
                    new OptionValueId(globalOptionValueId),
                    new Money(new BigDecimal("1000")),
                    true,
                    true
            );
            ProductOptionGroup pog = ProductOptionGroup.reconstitute(
                    new ProductOptionGroupId(UUID.randomUUID()),
                    new OptionGroupId(globalOptionGroupId),
                    1,
                    true,
                    List.of(pov)
            );
            product.addOptionGroup(pog);

            AddProductVariantCommand command = AddProductVariantCommand.builder()
                    .stockQuantity(2)
                    .selectedProductOptionValueIds(Set.of(productOptionValueId))
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);
            when(idGenerator.generateId()).thenReturn(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID()
            );
            doThrow(new ProductDomainException("Global SKU uniqueness violation"))
                    .doThrow(new ProductDomainException("Global SKU uniqueness violation"))
                    .doNothing()
                    .when(productDomainService).validateGlobalSkuUniqueness(anyString());

            AddProductVariantResult result = productApplicationService.addProductVariant(productId, command);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.sku()).startsWith("PRD-");
            verify(productDomainService, times(3)).validateGlobalSkuUniqueness(anyString());
            verify(idGenerator, times(3)).generateId();
            verify(productRepository).save(product);
        }

        @Test
        @DisplayName("SKU 충돌이 5회 연속이면 ProductDomainException")
        void retriesExceeded_ThrowsException() {
            UUID productId = UUID.randomUUID();
            Product product = createBaseProduct(productId);

            UUID globalOptionGroupId = UUID.randomUUID();
            UUID globalOptionValueId = UUID.randomUUID();
            UUID productOptionValueId = UUID.randomUUID();

            ProductOptionValue pov = ProductOptionValue.reconstitute(
                    new ProductOptionValueId(productOptionValueId),
                    new OptionValueId(globalOptionValueId),
                    new Money(new BigDecimal("1000")),
                    true,
                    true
            );
            ProductOptionGroup pog = ProductOptionGroup.reconstitute(
                    new ProductOptionGroupId(UUID.randomUUID()),
                    new OptionGroupId(globalOptionGroupId),
                    1,
                    true,
                    List.of(pov)
            );
            product.addOptionGroup(pog);

            AddProductVariantCommand command = AddProductVariantCommand.builder()
                    .stockQuantity(2)
                    .selectedProductOptionValueIds(Set.of(productOptionValueId))
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            when(idGenerator.generateId()).thenReturn(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID()
            );
            doThrow(new ProductDomainException("Global SKU uniqueness violation"))
                    .when(productDomainService).validateGlobalSkuUniqueness(anyString());

            assertThatThrownBy(() -> productApplicationService.addProductVariant(productId, command))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Failed to generate unique SKU after 5 attempts");

            verify(productDomainService, times(5)).validateGlobalSkuUniqueness(anyString());
            verify(idGenerator, times(5)).generateId();
            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("changeProductOptionValuePriceDelta")
    class ChangePriceDeltaTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void invalidRequest_Throws() {
            assertThatThrownBy(() -> productApplicationService.changeProductOptionValuePriceDelta(null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid product option value price delta change request");
        }

        @Test
        @DisplayName("정상 변경 시 결과 반환")
        void success() {
            UUID productId = UUID.randomUUID();
            UUID productOptionValueId = UUID.randomUUID();

            ProductOptionValue pov = ProductOptionValue.reconstitute(
                    new ProductOptionValueId(productOptionValueId),
                    new OptionValueId(UUID.randomUUID()),
                    new Money(new BigDecimal("1000")),
                    true,
                    true
            );

            ProductOptionGroup pog = ProductOptionGroup.reconstitute(
                    new ProductOptionGroupId(UUID.randomUUID()),
                    new OptionGroupId(UUID.randomUUID()),
                    1,
                    true,
                    List.of(pov)
            );

            Product product = Product.reconstitute(
                    new ProductId(productId),
                    null,
                    "상품",
                    "상품 설명은 20자 이상으로 충분히 길어야 합니다.",
                    new Money(new BigDecimal("10000")),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW,
                    "브랜드",
                    "https://example.com/image.jpg",
                    List.of(pog),
                    List.of()
            );

            ChangeProductOptionValuePriceDeltaCommand command =
                    ChangeProductOptionValuePriceDeltaCommand.builder()
                            .priceDelta(new BigDecimal("2500"))
                            .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);

            ChangeProductOptionValuePriceDeltaResult result = productApplicationService
                    .changeProductOptionValuePriceDelta(productId, productOptionValueId, command);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.productOptionValueId()).isEqualTo(productOptionValueId);
            assertThat(result.priceDelta()).isEqualByComparingTo(new BigDecimal("2500.00"));

            verify(productRepository).save(product);
        }
    }

    @Nested
    @DisplayName("updateProductVariant")
    class UpdateProductVariantTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void invalidRequest_Throws() {
            assertThatThrownBy(() -> productApplicationService.updateProductVariant(null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid product variant update request");
        }

        @Test
        @DisplayName("변형이 없으면 ProductDomainException")
        void variantNotFound_Throws() {
            UUID productId = UUID.randomUUID();
            UUID variantId = UUID.randomUUID();
            Product product = createBaseProduct(productId);

            UpdateProductVariantCommand command = UpdateProductVariantCommand.builder()
                    .stockQuantity(10)
                    .status(ProductStatus.ACTIVE)
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> productApplicationService.updateProductVariant(productId, variantId, command))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Variant not found in this product");
        }

        @Test
        @DisplayName("재고/상태 업데이트 성공")
        void success() {
            UUID productId = UUID.randomUUID();
            UUID variantId = UUID.randomUUID();

            ProductVariant variant = ProductVariant.reconstitute(
                    new ProductVariantId(variantId),
                    "SKU-001",
                    1,
                    ProductStatus.ACTIVE,
                    new Money(new BigDecimal("11000")),
                    Set.of()
            );

            Product product = Product.reconstitute(
                    new ProductId(productId),
                    null,
                    "상품",
                    "상품 설명은 20자 이상으로 충분히 길어야 합니다.",
                    new Money(new BigDecimal("10000")),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW,
                    "브랜드",
                    "https://example.com/image.jpg",
                    List.of(),
                    List.of(variant)
            );

            UpdateProductVariantCommand command = UpdateProductVariantCommand.builder()
                    .stockQuantity(0)
                    .status(ProductStatus.INACTIVE)
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);

            UpdateProductVariantResult result = productApplicationService.updateProductVariant(productId, variantId, command);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.productVariantId()).isEqualTo(variantId);
            assertThat(result.stockQuantity()).isEqualTo(0);
            assertThat(result.status()).isEqualTo(ProductStatus.INACTIVE);

            verify(productRepository).save(product);
        }
    }

    @Nested
    @DisplayName("deleteProductVariant")
    class DeleteProductVariantTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void invalidRequest_Throws() {
            assertThatThrownBy(() -> productApplicationService.deleteProductVariant(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid product variant delete request");
        }

        @Test
        @DisplayName("정상 삭제 시 변형 상태가 DELETED가 된다")
        void success() {
            UUID productId = UUID.randomUUID();
            UUID variantId = UUID.randomUUID();

            ProductVariant variant = ProductVariant.reconstitute(
                    new ProductVariantId(variantId),
                    "SKU-001",
                    3,
                    ProductStatus.ACTIVE,
                    new Money(new BigDecimal("11000")),
                    Set.of()
            );

            Product product = Product.reconstitute(
                    new ProductId(productId),
                    null,
                    "상품",
                    "상품 설명은 20자 이상으로 충분히 길어야 합니다.",
                    new Money(new BigDecimal("10000")),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW,
                    "브랜드",
                    "https://example.com/image.jpg",
                    List.of(),
                    List.of(variant)
            );

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);

            DeleteProductVariantResult result = productApplicationService.deleteProductVariant(productId, variantId);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.productVariantId()).isEqualTo(variantId);
            assertThat(result.status()).isEqualTo(ProductStatus.DELETED);
            verify(productRepository).save(product);
        }
    }

    @Nested
    @DisplayName("deactivateProductOptionValue")
    class DeactivateProductOptionValueTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void invalidRequest_Throws() {
            assertThatThrownBy(() -> productApplicationService.deactivateProductOptionValue(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid product option value deactivate request");
        }

        @Test
        @DisplayName("정상 비활성화 시 active=false 반환")
        void success() {
            UUID productId = UUID.randomUUID();
            UUID productOptionValueId = UUID.randomUUID();

            ProductOptionValue pov = ProductOptionValue.reconstitute(
                    new ProductOptionValueId(productOptionValueId),
                    new OptionValueId(UUID.randomUUID()),
                    new Money(new BigDecimal("500")),
                    false,
                    true
            );
            ProductOptionGroup pog = ProductOptionGroup.reconstitute(
                    new ProductOptionGroupId(UUID.randomUUID()),
                    new OptionGroupId(UUID.randomUUID()),
                    1,
                    true,
                    List.of(pov)
            );

            Product product = Product.reconstitute(
                    new ProductId(productId),
                    null,
                    "상품",
                    "상품 설명은 20자 이상으로 충분히 길어야 합니다.",
                    new Money(new BigDecimal("10000")),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW,
                    "브랜드",
                    "https://example.com/image.jpg",
                    List.of(pog),
                    List.of()
            );

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);

            DeactivateProductOptionValueResult result =
                    productApplicationService.deactivateProductOptionValue(productId, productOptionValueId);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.productOptionValueId()).isEqualTo(productOptionValueId);
            assertThat(result.active()).isFalse();
            assertThat(result.priceDelta()).isEqualByComparingTo(new BigDecimal("500.00"));
            verify(productRepository).save(product);
        }
    }
}
