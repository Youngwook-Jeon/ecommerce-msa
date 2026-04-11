package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.command.CreateProductCommand;
import com.project.young.productservice.application.dto.command.UpdateProductCommand;
import com.project.young.productservice.application.dto.command.UpdateProductStatusCommand;
import com.project.young.productservice.application.dto.result.CreateProductResult;
import com.project.young.productservice.application.dto.result.DeleteProductResult;
import com.project.young.productservice.application.dto.result.UpdateProductResult;
import com.project.young.productservice.application.mapper.ProductDataMapper;
import com.project.young.productservice.application.port.output.IdGenerator;
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
import java.util.List;
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

    @Mock
    private IdGenerator idGenerator;

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
                    .build();

            CategoryId categoryId = new CategoryId(1L);

            UUID generatedId = UUID.randomUUID();
            ProductId generatedProductId = new ProductId(generatedId);

            Product toSave = Product.builder()
                    .productId(generatedProductId)
                    .categoryId(categoryId)
                    .name("상품")
                    .description("상품에 대한 설명입니다. 충분히 깁니다.")
                    .basePrice(new Money(new BigDecimal("10000")))
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.DRAFT)
                    .build();

            CreateProductResult expected = CreateProductResult.builder()
                    .id(generatedId)
                    .name("상품")
                    .build();

            when(idGenerator.generateId()).thenReturn(generatedId);
            when(productDataMapper.toDraftProduct(command, categoryId, generatedProductId)).thenReturn(toSave);
            doNothing().when(productRepository).insert(toSave);
            when(productDataMapper.toCreateProductResult(toSave)).thenReturn(expected);

            // When
            CreateProductResult result = productApplicationService.createProduct(command);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(generatedId);
            assertThat(result.name()).isEqualTo("상품");

            verify(productDomainService).validateCategoryForProduct(categoryId);
            verify(idGenerator).generateId();
            verify(productDataMapper).toDraftProduct(command, categoryId, generatedProductId);
            verify(productRepository).insert(toSave);
            verify(productDataMapper).toCreateProductResult(toSave);
        }

        @Test
        @DisplayName("ID를 선생성해서 저장 커맨드에 전달한다")
        void createProduct_GeneratesIdBeforeSave() {
            // Given
            CreateProductCommand command = CreateProductCommand.builder()
                    .name("새 상품")
                    .description("상품에 대한 설명입니다. 충분히 깁니다.")
                    .basePrice(new BigDecimal("10000"))
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .categoryId(null)
                    .conditionType(ConditionType.NEW)
                    .build();
            UUID generatedId = UUID.randomUUID();
            ProductId generatedProductId = new ProductId(generatedId);

            Product toSave = Product.builder()
                    .productId(generatedProductId)
                    .categoryId(null)
                    .name("새 상품")
                    .description("상품에 대한 설명입니다. 충분히 깁니다.")
                    .basePrice(new Money(new BigDecimal("10000")))
                    .brand("브랜드")
                    .mainImageUrl("url")
                    .conditionType(ConditionType.NEW)
                    .status(ProductStatus.DRAFT)
                    .build();

            CreateProductResult expected = CreateProductResult.builder()
                    .id(generatedId)
                    .name("새 상품")
                    .build();

            when(idGenerator.generateId()).thenReturn(generatedId);
            when(productDataMapper.toDraftProduct(command, null, generatedProductId)).thenReturn(toSave);
            doNothing().when(productRepository).insert(toSave);
            when(productDataMapper.toCreateProductResult(toSave)).thenReturn(expected);

            // When
            CreateProductResult result = productApplicationService.createProduct(command);

            // Then
            assertThat(result.id()).isEqualTo(generatedId);
            verify(idGenerator).generateId();
            verify(productDataMapper).toDraftProduct(command, null, generatedProductId);
            verify(productRepository).insert(toSave);
            verify(productDataMapper).toCreateProductResult(toSave);
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
                    .build();

            UUID rawId = UUID.randomUUID();
            when(productRepository.findById(new ProductId(rawId))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productApplicationService.updateProduct(rawId, command))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("Product with id");

            verify(productRepository, never()).update(any());
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
                    "url",
                    List.of(),
                    List.of()
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
            doNothing().when(productRepository).update(product);
            when(productDataMapper.toUpdateProductResult(product)).thenReturn(expected);

            // When
            UpdateProductResult result = productApplicationService.updateProduct(rawId, command);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("수정된 이름");
            verify(productRepository).update(product);
            verify(productDataMapper).toUpdateProductResult(product);
        }

    }

    @Nested
    @DisplayName("updateProductStatus")
    class UpdateProductStatusTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void updateProductStatus_InvalidRequest_ThrowsException() {
            assertThatThrownBy(() -> productApplicationService.updateProductStatus(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid product status update request");
        }

        @Test
        @DisplayName("상태 변경 시 ProductDomainService.validateStatusChangeRules 호출")
        void updateProductStatus_ValidatesStatusChangeRules() {
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);

            UpdateProductStatusCommand command = UpdateProductStatusCommand.builder()
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
                    "url",
                    List.of(),
                    List.of()
            );

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            doNothing().when(productRepository).update(product);
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

            UpdateProductResult result = productApplicationService.updateProductStatus(rawId, command);

            assertThat(result.status()).isEqualTo(ProductStatus.INACTIVE);
            verify(productDomainService).validateStatusChangeRules(product, ProductStatus.INACTIVE);
            verify(productRepository).update(product);
        }

        @Test
        @DisplayName("DRAFT 상품을 variant 없이 ACTIVE로 변경하면 ProductDomainException")
        void updateProductStatus_PublishWithoutVariant_Throws() {
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);

            UpdateProductStatusCommand command = UpdateProductStatusCommand.builder()
                    .status(ProductStatus.ACTIVE)
                    .build();

            Product draftProduct = Product.reconstitute(
                    productId,
                    null,
                    "상품",
                    "상품에 대한 설명입니다. 충분히 깁니다.",
                    new Money(new BigDecimal("1000")),
                    ProductStatus.DRAFT,
                    ConditionType.NEW,
                    "브랜드",
                    "url",
                    List.of(),
                    List.of()
            );

            when(productRepository.findById(productId)).thenReturn(Optional.of(draftProduct));

            assertThatThrownBy(() -> productApplicationService.updateProductStatus(rawId, command))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Cannot publish product without at least one variant");

            verify(productRepository, never()).update(any());
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

            verify(productDomainService, never()).validateDeletionRules(any());
            verify(productRepository, never()).update(any());
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
                    ProductStatus.ACTIVE,
                    ConditionType.NEW,
                    "브랜드",
                    "url",
                    List.of(),
                    List.of()
            );

            DeleteProductResult expected = DeleteProductResult.builder()
                    .id(rawId)
                    .name("삭제상품")
                    .build();

            when(productRepository.findById(productId)).thenReturn(Optional.of(deleted));
            doNothing().when(productRepository).update(deleted);
            when(productDataMapper.toDeleteProductResult(deleted)).thenReturn(expected);

            // When
            DeleteProductResult result = productApplicationService.deleteProduct(rawId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(rawId);
            assertThat(result.name()).isEqualTo("삭제상품");

            verify(productDomainService).validateDeletionRules(deleted);
            verify(productRepository).update(deleted);
            verify(productDataMapper).toDeleteProductResult(deleted);
        }
    }
}