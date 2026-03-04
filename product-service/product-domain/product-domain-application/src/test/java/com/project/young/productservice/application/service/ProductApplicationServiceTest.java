package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.*;
import com.project.young.productservice.application.mapper.ProductDataMapper;
import com.project.young.productservice.domain.entity.Product;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductDomainService productDomainService;

    @Mock
    private ProductDataMapper productDataMapper;

    @InjectMocks
    private ProductApplicationService productApplicationService;

    @Nested
    @DisplayName("createProduct")
    class CreateProductTests {

        @Test
        @DisplayName("command가 null이면 IllegalArgumentException")
        void createProduct_NullCommand_ThrowsException() {
            assertThatThrownBy(() -> productApplicationService.createProduct(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Create product command cannot be null");

            verifyNoInteractions(productDomainService, productRepository, productDataMapper);
        }

        @Test
        @DisplayName("정상 생성 시 mapper와 repository를 통해 Product 생성")
        void createProduct_Success() {
            // Given
            CreateProductCommand command = CreateProductCommand.builder()
                    .name("상품")
                    .description("설명입니다. 충분히 깁니다.")
                    .basePrice(new BigDecimal("10000"))
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .categoryId(1L)
                    .conditionType(ConditionType.NEW)
                    .productStatus(ProductStatus.ACTIVE)
                    .build();

            CategoryId categoryId = new CategoryId(1L);

            Product toSave = Product.builder()
                    .categoryId(categoryId)
                    .name("상품")
                    .description("상품에 대한 설명입니다. 충분히 깁니다.")
                    .basePrice(new Money(new BigDecimal("10000")))
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            UUID savedId = UUID.randomUUID();
            Product saved = Product.reconstitute(
                    new ProductId(savedId),
                    categoryId,
                    "상품",
                    "상품에 대한 설명입니다. 충분히 깁니다.",
                    new Money(new BigDecimal("10000")),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW,
                    "브랜드",
                    "url"
            );

            CreateProductResult expected = CreateProductResult.builder()
                    .id(savedId)
                    .name("상품")
                    .build();

            when(productDataMapper.toProduct(command, categoryId)).thenReturn(toSave);
            when(productRepository.save(toSave)).thenReturn(saved);
            when(productDataMapper.toCreateProductResult(saved)).thenReturn(expected);

            // When
            CreateProductResult result = productApplicationService.createProduct(command);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(savedId);
            assertThat(result.name()).isEqualTo("상품");

            verify(productDomainService).validateCategoryForProduct(categoryId);
            verify(productRepository).save(toSave);
            verify(productDataMapper).toCreateProductResult(saved);
        }

        @Test
        @DisplayName("저장 후 ID가 할당되지 않으면 ProductDomainException")
        void createProduct_IdNotAssigned_ThrowsException() {
            // Given
            CreateProductCommand command = CreateProductCommand.builder()
                    .name("새 상품")
                    .description("상품에 대한 설명입니다. 충분히 깁니다.")
                    .basePrice(new BigDecimal("10000"))
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .categoryId(null)
                    .conditionType(ConditionType.NEW)
                    .productStatus(ProductStatus.ACTIVE)
                    .build();

            Product toSave = Product.builder()
                    .categoryId(null)
                    .name("새 상품")
                    .description("상품에 대한 설명입니다. 충분히 깁니다.")
                    .basePrice(new Money(new BigDecimal("10000")))
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            Product savedWithoutId = Product.builder()
                    .categoryId(null)
                    .name("새 상품")
                    .description("상품에 대한 설명입니다. 충분히 깁니다.")
                    .basePrice(new Money(new BigDecimal("10000")))
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            when(productDataMapper.toProduct(command, null)).thenReturn(toSave);
            when(productRepository.save(toSave)).thenReturn(savedWithoutId);

            // When & Then
            assertThatThrownBy(() -> productApplicationService.createProduct(command))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Failed to assign ID to the new product.");

            verify(productDataMapper, never()).toCreateProductResult(any());
        }
    }

    @Nested
    @DisplayName("updateProduct")
    class UpdateProductTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void updateProduct_InvalidRequest_ThrowsException() {
            assertThatThrownBy(() -> productApplicationService.updateProduct(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid product update request");
        }

        @Test
        @DisplayName("대상이 없으면 ProductNotFoundException")
        void updateProduct_NotFound_ThrowsException() {
            // Given
            UpdateProductCommand command = UpdateProductCommand.builder()
                    .name("변경")
                    .description("상품에 대한 설명입니다. 충분히 깁니다.")
                    .basePrice(new BigDecimal("1000"))
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            UUID rawId = UUID.randomUUID();
            when(productRepository.findById(new ProductId(rawId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productApplicationService.updateProduct(rawId, command))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("Product with id");

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("이름 변경 시 저장하고 UpdateProductResult 반환")
        void updateProduct_ChangeName_SavesAndReturnsResult() {
            // Given
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);

            UpdateProductCommand command = UpdateProductCommand.builder()
                    .name("수정된 이름")
                    .description("상품에 대한 설명입니다. 충분히 깁니다.")
                    .basePrice(new BigDecimal("1000"))
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.ACTIVE)
                    .build();

            Product product = Product.reconstitute(
                    productId,
                    null,
                    "기존 이름",
                    "상품에 대한 설명입니다. 충분히 깁니다.",
                    new Money(new BigDecimal("1000")),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW,
                    "브랜드",
                    "url"
            );

            UpdateProductResult expected = UpdateProductResult.builder()
                    .id(rawId)
                    .name("수정된 이름")
                    .categoryId(null)
                    .description("상품에 대한 설명입니다. 충분히 깁니다.")
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .basePrice(new BigDecimal("1000"))
                    .status(ProductStatus.ACTIVE)
                    .conditionType(ConditionType.NEW)
                    .build();

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);
            when(productDataMapper.toUpdateProductResult(product)).thenReturn(expected);

            // When
            UpdateProductResult result = productApplicationService.updateProduct(rawId, command);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("수정된 이름");
            verify(productRepository).save(product);
            verify(productDataMapper).toUpdateProductResult(product);
        }

        @Test
        @DisplayName("상태 변경 시 ProductDomainService.validateStatusChangeRules 호출")
        void updateProduct_StatusChange_ValidatesStatusChangeRules() {
            // Given
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);

            UpdateProductCommand command = UpdateProductCommand.builder()
                    .name("상품")
                    .description("상품에 대한 설명입니다. 충분히 깁니다.")
                    .basePrice(new BigDecimal("1000"))
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.INACTIVE)
                    .build();

            Product product = Product.reconstitute(
                    productId,
                    null,
                    "상품",
                    "상품에 대한 설명입니다. 충분히 깁니다.",
                    new Money(new BigDecimal("1000")),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW,
                    "브랜드",
                    "url"
            );

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);
            when(productDataMapper.toUpdateProductResult(product)).thenReturn(
                    UpdateProductResult.builder()
                            .id(rawId)
                            .name("상품")
                            .categoryId(null)
                            .description("상품에 대한 설명입니다. 충분히 깁니다.")
                            .brand("브랜드")
                            .mainImageUrl("url")
                            .basePrice(new BigDecimal("1000"))
                            .status(ProductStatus.INACTIVE)
                            .conditionType(ConditionType.NEW)
                            .build()
            );

            // When
            UpdateProductResult result = productApplicationService.updateProduct(rawId, command);

            // Then
            assertThat(result.status()).isEqualTo(ProductStatus.INACTIVE);
            verify(productDomainService).validateStatusChangeRules(product, ProductStatus.INACTIVE);
            verify(productRepository).save(product);
        }
    }

    @Nested
    @DisplayName("deleteProduct")
    class DeleteProductTests {

        @Test
        @DisplayName("null id이면 IllegalArgumentException")
        void deleteProduct_NullId_ThrowsException() {
            assertThatThrownBy(() -> productApplicationService.deleteProduct(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Product ID for delete cannot be null.");

            verify(productDomainService, never()).prepareForDeletion(any());
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("삭제 성공 시 DeleteProductResult를 반환한다")
        void deleteProduct_Success() {
            // Given
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);

            Product deleted = Product.reconstitute(
                    productId,
                    null,
                    "삭제상품",
                    "상품에 대한 설명입니다. 충분히 깁니다.",
                    new Money(new BigDecimal("1000")),
                    ProductStatus.DELETED,
                    ConditionType.NEW,
                    "브랜드",
                    "url"
            );

            DeleteProductResult expected = DeleteProductResult.builder()
                    .id(rawId)
                    .name("삭제상품")
                    .build();

            when(productDomainService.prepareForDeletion(productId)).thenReturn(deleted);
            when(productDataMapper.toDeleteProductResult(deleted)).thenReturn(expected);

            // When
            DeleteProductResult result = productApplicationService.deleteProduct(rawId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(rawId);
            assertThat(result.name()).isEqualTo("삭제상품");

            verify(productDomainService).prepareForDeletion(productId);
            verify(productDataMapper).toDeleteProductResult(deleted);
        }
    }
}