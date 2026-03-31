package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductDomainServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductDomainServiceImpl productDomainService;

    @Nested
    @DisplayName("validateCategoryForProduct")
    class ValidateCategoryForProductTests {

        @Test
        @DisplayName("categoryId가 null이면 아무 것도 하지 않는다")
        void validateCategoryForProduct_NullCategoryId_DoesNothing() {
            productDomainService.validateCategoryForProduct(null);
            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("카테고리가 없으면 ProductDomainException")
        void validateCategoryForProduct_NotFound_Throws() {
            CategoryId categoryId = new CategoryId(1L);
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productDomainService.validateCategoryForProduct(categoryId))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Category with id 1 not found");

            verify(categoryRepository).findById(categoryId);
        }

        @Test
        @DisplayName("DELETED 카테고리면 ProductDomainException")
        void validateCategoryForProduct_DeletedCategory_Throws() {
            CategoryId categoryId = new CategoryId(1L);
            Category deletedCategory = Category.reconstitute(
                    categoryId, "카테고리", null, CategoryStatus.DELETED);

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(deletedCategory));

            assertThatThrownBy(() -> productDomainService.validateCategoryForProduct(categoryId))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Product cannot be assigned to a DELETED category");

            verify(categoryRepository).findById(categoryId);
        }

        @Test
        @DisplayName("ACTIVE/INACTIVE 카테고리는 허용")
        void validateCategoryForProduct_NonDeleted_Allows() {
            CategoryId categoryId = new CategoryId(1L);
            Category activeCategory = Category.reconstitute(
                    categoryId, "카테고리", null, CategoryStatus.ACTIVE);

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));

            productDomainService.validateCategoryForProduct(categoryId);

            verify(categoryRepository).findById(categoryId);
        }
    }

    @Nested
    @DisplayName("validateStatusChangeRules")
    class ValidateStatusChangeRulesTests {

        @Test
        @DisplayName("product가 null이면 ProductDomainException")
        void validateStatusChangeRules_NullProduct_Throws() {
            assertThatThrownBy(() -> productDomainService.validateStatusChangeRules(null, ProductStatus.ACTIVE))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Product must not be null");
        }

        @Test
        @DisplayName("newStatus가 null이면 ProductDomainException")
        void validateStatusChangeRules_NullStatus_Throws() {
            Product product = sampleProduct(ProductStatus.ACTIVE);

            assertThatThrownBy(() -> productDomainService.validateStatusChangeRules(product, null))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("New product status must not be null");
        }

        @Test
        @DisplayName("삭제된 상품이면 상태 변경 불가")
        void validateStatusChangeRules_DeletedProduct_Throws() {
            Product product = sampleProduct(ProductStatus.DELETED);

            assertThatThrownBy(() -> productDomainService.validateStatusChangeRules(product, ProductStatus.ACTIVE))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Cannot change status of a deleted product");
        }

        @Test
        @DisplayName("동일 상태로 변경은 no-op")
        void validateStatusChangeRules_SameStatus_NoOp() {
            Product product = sampleProduct(ProductStatus.ACTIVE);

            productDomainService.validateStatusChangeRules(product, ProductStatus.ACTIVE);
            // 예외가 없으면 통과
        }

        @Test
        @DisplayName("유효한 상태 전환이면 통과")
        void validateStatusChangeRules_ValidTransition_Allows() {
            Product product = sampleProduct(ProductStatus.ACTIVE);

            productDomainService.validateStatusChangeRules(product, ProductStatus.INACTIVE);
            // 예외가 없으면 통과
        }
    }

    @Nested
    @DisplayName("validateGlobalSkuUniqueness")
    class ValidateGlobalSkuUniquenessTests {
        @Test
        @DisplayName("sku가 null/blank면 ProductDomainException")
        void nullOrBlankSku_Throws() {
            assertThatThrownBy(() -> productDomainService.validateGlobalSkuUniqueness(null))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("SKU cannot be empty");

            assertThatThrownBy(() -> productDomainService.validateGlobalSkuUniqueness(" "))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("SKU cannot be empty");
        }

        @Test
        @DisplayName("이미 존재하는 sku면 ProductDomainException")
        void existingSku_Throws() {
            when(productRepository.existsBySku("SKU-001")).thenReturn(true);

            assertThatThrownBy(() -> productDomainService.validateGlobalSkuUniqueness("SKU-001"))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Global SKU uniqueness violation");
        }

        @Test
        @DisplayName("존재하지 않는 sku면 통과")
        void nonExistingSku_Allows() {
            when(productRepository.existsBySku("SKU-NEW-001")).thenReturn(false);

            productDomainService.validateGlobalSkuUniqueness("SKU-NEW-001");

            verify(productRepository).existsBySku("SKU-NEW-001");
        }
    }

    @Nested
    @DisplayName("validateDeletionRules")
    class ValidateDeletionRulesTests {

        @Test
        @DisplayName("product가 null이면 ProductDomainException")
        void validateDeletionRules_NullProduct_Throws() {
            assertThatThrownBy(() -> productDomainService.validateDeletionRules(null))
                    .isInstanceOf(ProductDomainException.class)
                    .hasMessageContaining("Product must not be null");
        }

        @Test
        @DisplayName("정상 상품이면 통과")
        void validateDeletionRules_ValidProduct_Allows() {
            Product product = sampleProduct(ProductStatus.ACTIVE);
            assertThatCode(() -> productDomainService.validateDeletionRules(product))
                    .doesNotThrowAnyException();
        }
    }

    private Product sampleProduct(ProductStatus status) {
        return Product.reconstitute(
                new ProductId(UUID.randomUUID()),
                null,
                "상품",
                "적당히 긴 유효한 설명입니다. 20자 이상.",
                new Money(new BigDecimal("10000")),
                status,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}

