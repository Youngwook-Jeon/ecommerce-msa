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
import java.util.*;

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
                ProductStatus.DRAFT,
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
            doNothing().when(productRepository).update(product);
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

            verify(productRepository).update(product);
            verify(productDomainService).validateOptionValueBelongsToGroup(
                    new OptionGroupId(optionGroupId),
                    new OptionValueId(optionValueId)
            );
        }

        @Test
        @DisplayName("ACTIVE 상품에는 옵션 그룹을 추가할 수 없다")
        void activeProduct_CannotAddOptionGroup() {
            UUID productId = UUID.randomUUID();
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
                    List.of()
            );

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

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> productApplicationService.addProductOptionGroup(productId, command))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Cannot add option groups after product is ACTIVE");

            verify(productRepository, never()).update(any());
        }

        @Test
        @DisplayName("옵션 값이 없으면 IllegalArgumentException")
        void emptyOptionValues_Throws() {
            UUID productId = UUID.randomUUID();
            Product product = createBaseProduct(productId);
            UUID globalOptionGroupId = UUID.randomUUID();

            AddProductOptionGroupCommand command = AddProductOptionGroupCommand.builder()
                    .optionGroupId(globalOptionGroupId)
                    .stepOrder(1)
                    .required(true)
                    .optionValues(null)
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

            assertThatThrownBy(() -> productApplicationService.addProductOptionGroup(productId, command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Product option values must not be empty");

            verify(productRepository, never()).update(any());
        }
    }

    @Nested
    @DisplayName("addProductOptionValue")
    class AddProductOptionValueTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void invalidRequest_Throws() {
            assertThatThrownBy(() -> productApplicationService.addProductOptionValue(null, UUID.randomUUID(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid add product option value request");
        }

        @Test
        @DisplayName("존재하지 않는 productOptionGroupId면 ProductDomainException")
        void unknownGroup_Throws() {
            UUID productId = UUID.randomUUID();
            Product product = createBaseProduct(productId);
            UUID pogId = UUID.randomUUID();

            AddProductOptionValueCommand command = AddProductOptionValueCommand.builder()
                    .optionValueId(UUID.randomUUID())
                    .priceDelta(BigDecimal.ZERO)
                    .isDefault(false)
                    .isActive(true)
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> productApplicationService.addProductOptionValue(productId, pogId, command))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Product option group not found");

            verify(productRepository, never()).update(any());
        }

        @Test
        @DisplayName("빈 그룹에 옵션 값을 추가하면 저장된다")
        void success() {
            UUID productId = UUID.randomUUID();
            Product product = createBaseProduct(productId);
            UUID globalOptionGroupId = UUID.randomUUID();
            UUID productOptionGroupId = UUID.randomUUID();
            UUID globalOptionValueId = UUID.randomUUID();
            UUID generatedProductOptionValueId = UUID.randomUUID();

            ProductOptionGroup pog = ProductOptionGroup.builder()
                    .id(new ProductOptionGroupId(productOptionGroupId))
                    .optionGroupId(new OptionGroupId(globalOptionGroupId))
                    .stepOrder(1)
                    .isRequired(true)
                    .optionValues(new ArrayList<>())
                    .build();
            product.addOptionGroup(pog);

            AddProductOptionValueCommand command = AddProductOptionValueCommand.builder()
                    .optionValueId(globalOptionValueId)
                    .priceDelta(new BigDecimal("500"))
                    .isDefault(true)
                    .isActive(true)
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            doNothing().when(productRepository).update(product);
            when(idGenerator.generateId()).thenReturn(generatedProductOptionValueId);

            AddProductOptionValueToGroupResult result =
                    productApplicationService.addProductOptionValue(productId, productOptionGroupId, command);

            assertThat(result.productOptionGroupId()).isEqualTo(productOptionGroupId);
            assertThat(result.productOptionValueId()).isEqualTo(generatedProductOptionValueId);
            assertThat(result.optionValueId()).isEqualTo(globalOptionValueId);
            assertThat(product.getOptionGroups().getFirst().getOptionValues()).hasSize(1);

            verify(productRepository).update(product);
            verify(productDomainService).validateOptionValueBelongsToGroup(
                    new OptionGroupId(globalOptionGroupId),
                    new OptionValueId(globalOptionValueId)
            );
        }

        @Test
        @DisplayName("글로벌 그룹에 속하지 않은 옵션 값이면 예외")
        void optionValueNotBelongingToGlobalGroup_Throws() {
            UUID productId = UUID.randomUUID();
            Product product = createBaseProduct(productId);
            UUID globalOptionGroupId = UUID.randomUUID();
            UUID productOptionGroupId = UUID.randomUUID();
            UUID wrongGlobalOptionValueId = UUID.randomUUID();

            ProductOptionGroup pog = ProductOptionGroup.builder()
                    .id(new ProductOptionGroupId(productOptionGroupId))
                    .optionGroupId(new OptionGroupId(globalOptionGroupId))
                    .stepOrder(1)
                    .isRequired(true)
                    .optionValues(new ArrayList<>())
                    .build();
            product.addOptionGroup(pog);

            AddProductOptionValueCommand command = AddProductOptionValueCommand.builder()
                    .optionValueId(wrongGlobalOptionValueId)
                    .priceDelta(new BigDecimal("500"))
                    .isDefault(true)
                    .isActive(true)
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            doThrow(new ProductDomainException("does not belong"))
                    .when(productDomainService)
                    .validateOptionValueBelongsToGroup(
                            new OptionGroupId(globalOptionGroupId),
                            new OptionValueId(wrongGlobalOptionValueId)
                    );

            assertThatThrownBy(() -> productApplicationService.addProductOptionValue(productId, productOptionGroupId, command))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("does not belong");

            verify(productRepository, never()).update(any());
        }

        @Test
        @DisplayName("ACTIVE 상품에도 기존 그룹에 옵션 값 추가는 가능하다")
        void activeProduct_CanAddOptionValue() {
            UUID productId = UUID.randomUUID();
            UUID globalOptionGroupId = UUID.randomUUID();
            UUID productOptionGroupId = UUID.randomUUID();

            ProductOptionGroup pog = ProductOptionGroup.builder()
                    .id(new ProductOptionGroupId(productOptionGroupId))
                    .optionGroupId(new OptionGroupId(globalOptionGroupId))
                    .stepOrder(1)
                    .isRequired(true)
                    .optionValues(new ArrayList<>())
                    .build();

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
                    new ArrayList<>(List.of(pog)),
                    new ArrayList<>()
            );

            AddProductOptionValueCommand command = AddProductOptionValueCommand.builder()
                    .optionValueId(UUID.randomUUID())
                    .priceDelta(BigDecimal.ZERO)
                    .isDefault(false)
                    .isActive(true)
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            doNothing().when(productRepository).update(product);
            when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

            assertThatCode(() -> productApplicationService.addProductOptionValue(productId, productOptionGroupId, command))
                    .doesNotThrowAnyException();

            verify(productRepository).update(product);
        }
    }

    @Nested
    @DisplayName("addProductVariants")
    class AddProductVariantsTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void invalidRequest_Throws() {
            assertThatThrownBy(() -> productApplicationService.addProductVariants(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid add product variants request");
        }

        @Test
        @DisplayName("요청 내부 SKU 충돌이 5회 연속이면 ProductDomainException")
        void duplicateSku_Throws() {
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
                    .stockQuantity(5)
                    .selectedProductOptionValueIds(Set.of(productOptionValueId))
                    .build();

            AddProductVariantsCommand bulkCommand = AddProductVariantsCommand.builder()
                    .variants(List.of(command, command))
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            UUID sameVariantId = UUID.randomUUID();
            when(idGenerator.generateId()).thenReturn(
                    sameVariantId,
                    sameVariantId,
                    sameVariantId,
                    sameVariantId,
                    sameVariantId,
                    sameVariantId
            );

            assertThatThrownBy(() -> productApplicationService.addProductVariants(productId, bulkCommand))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Failed to generate unique SKU after 5 attempts");

            verify(productRepository, never()).update(any());
            verifyNoInteractions(productDomainService);
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

            AddProductVariantsCommand bulkCommand = AddProductVariantsCommand.builder()
                    .variants(List.of(command))
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(product));
            doNothing().when(productRepository).update(product);
            UUID generatedVariantId = UUID.randomUUID();
            when(idGenerator.generateId()).thenReturn(generatedVariantId);

            AddProductVariantResult result = productApplicationService.addProductVariants(productId, bulkCommand).get(0);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.productVariantId()).isEqualTo(generatedVariantId);
            assertThat(result.sku()).startsWith("PRD-");
            assertThat(result.stockQuantity()).isEqualTo(3);
            assertThat(result.calculatedPrice()).isEqualByComparingTo(new BigDecimal("11000.00"));
            assertThat(product.getVariants()).hasSize(1);

            verify(productRepository).update(product);
            verifyNoInteractions(productDomainService);
        }

        @Test
        @DisplayName("삭제된 상품에는 variant를 추가할 수 없다")
        void deletedProduct_CannotAddVariant() {
            UUID productId = UUID.randomUUID();
            Product deletedProduct = Product.reconstitute(
                    new ProductId(productId),
                    null,
                    "삭제 상품",
                    "상품 설명은 20자 이상으로 충분히 길어야 합니다.",
                    new Money(new BigDecimal("10000")),
                    ProductStatus.DELETED,
                    ConditionType.NEW,
                    "브랜드",
                    "https://example.com/image.jpg",
                    List.of(),
                    List.of()
            );

            AddProductVariantCommand command = AddProductVariantCommand.builder()
                    .stockQuantity(1)
                    .selectedProductOptionValueIds(Set.of(UUID.randomUUID()))
                    .build();

            AddProductVariantsCommand bulkCommand = AddProductVariantsCommand.builder()
                    .variants(List.of(command))
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.of(deletedProduct));

            assertThatThrownBy(() -> productApplicationService.addProductVariants(productId, bulkCommand))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Cannot update a product that has been deleted");

            verify(productRepository, never()).update(any());
            verifyNoInteractions(productDomainService);
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
            doNothing().when(productRepository).update(product);

            ChangeProductOptionValuePriceDeltaResult result = productApplicationService
                    .changeProductOptionValuePriceDelta(productId, productOptionValueId, command);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.productOptionValueId()).isEqualTo(productOptionValueId);
            assertThat(result.priceDelta()).isEqualByComparingTo(new BigDecimal("2500.00"));

            verify(productRepository).update(product);
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
        @DisplayName("상품이 없으면 ProductNotFoundException")
        void productNotFound_Throws() {
            UUID productId = UUID.randomUUID();
            UUID variantId = UUID.randomUUID();
            UpdateProductVariantCommand command = UpdateProductVariantCommand.builder()
                    .stockQuantity(10)
                    .status(ProductStatus.ACTIVE)
                    .build();

            when(productRepository.findById(new ProductId(productId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productApplicationService.updateProductVariant(productId, variantId, command))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("Product with id");

            verify(productRepository, never()).update(any());
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
            doNothing().when(productRepository).update(product);

            UpdateProductVariantResult result = productApplicationService.updateProductVariant(productId, variantId, command);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.productVariantId()).isEqualTo(variantId);
            assertThat(result.stockQuantity()).isEqualTo(0);
            assertThat(result.status()).isEqualTo(ProductStatus.INACTIVE);

            verify(productRepository).update(product);
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
            doNothing().when(productRepository).update(product);

            DeleteProductVariantResult result = productApplicationService.deleteProductVariant(productId, variantId);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.productVariantId()).isEqualTo(variantId);
            assertThat(result.status()).isEqualTo(ProductStatus.DELETED);
            verify(productRepository).update(product);
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
            doNothing().when(productRepository).update(product);

            DeactivateProductOptionValueResult result =
                    productApplicationService.deactivateProductOptionValue(productId, productOptionValueId);

            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.productOptionValueId()).isEqualTo(productOptionValueId);
            assertThat(result.active()).isFalse();
            assertThat(result.priceDelta()).isEqualByComparingTo(new BigDecimal("500.00"));
            verify(productRepository).update(product);
        }
    }
}
