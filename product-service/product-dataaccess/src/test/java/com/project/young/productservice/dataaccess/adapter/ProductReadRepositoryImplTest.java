package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.entity.VariantOptionValueEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductReadRepositoryImplTest {

    @Mock
    private ProductJpaRepository productJpaRepository;

    @Mock
    private ProductDataAccessMapper productDataAccessMapper;

    @InjectMocks
    private ProductReadRepositoryImpl productReadRepository;

    @Nested
    @DisplayName("findAllVisibleProducts 테스트")
    class FindAllVisibleProductsTests {

        @Test
        @DisplayName("전체 보이는 상품 목록 조회 성공")
        void findAllVisibleProducts_Success() {
            // Given
            Instant now = Instant.now();

            CategoryEntity category = CategoryEntity.builder()
                    .id(1L)
                    .name("의류")
                    .status(CategoryStatusEntity.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            ProductEntity product1 = ProductEntity.builder()
                    .id(UUID.randomUUID())
                    .category(category)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .basePrice(new BigDecimal("99000"))
                    .status(ProductStatusEntity.ACTIVE)
                    .conditionType(ConditionTypeEntity.NEW)
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image1.jpg")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            ProductEntity product2 = ProductEntity.builder()
                    .id(UUID.randomUUID())
                    .category(category)
                    .name("스트레이트핏 데님")
                    .description("스트레이트핏 데님 상세 설명입니다.")
                    .basePrice(new BigDecimal("89000"))
                    .status(ProductStatusEntity.ACTIVE)
                    .conditionType(ConditionTypeEntity.NEW)
                    .brand("브랜드B")
                    .mainImageUrl("https://example.com/image2.jpg")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            when(productJpaRepository.findAllVisible(ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE))
                    .thenReturn(List.of(product1, product2));

            when(productDataAccessMapper.toDomainStatus(ProductStatusEntity.ACTIVE))
                    .thenReturn(ProductStatus.ACTIVE);
            when(productDataAccessMapper.toDomainConditionType(ConditionTypeEntity.NEW))
                    .thenReturn(ConditionType.NEW);

            // When
            List<ReadProductView> result = productReadRepository.findAllVisibleProducts();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result)
                    .extracting(ReadProductView::name)
                    .containsExactlyInAnyOrder("와이드핏 데님", "스트레이트핏 데님");

            verify(productJpaRepository).findAllVisible(ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE);
        }
    }

    @Nested
    @DisplayName("findVisibleByCategoryId 테스트")
    class FindVisibleByCategoryIdTests {

        @Test
        @DisplayName("카테고리 ID로 보이는 상품 목록 조회 성공")
        void findVisibleByCategoryId_Success() {
            // Given
            CategoryId categoryId = new CategoryId(1L);
            Instant now = Instant.now();

            CategoryEntity categoryEntity = CategoryEntity.builder()
                    .id(1L)
                    .name("의류")
                    .status(CategoryStatusEntity.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            ProductEntity productEntity = ProductEntity.builder()
                    .id(UUID.randomUUID())
                    .category(categoryEntity)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .basePrice(new BigDecimal("99000"))
                    .status(ProductStatusEntity.ACTIVE)
                    .conditionType(ConditionTypeEntity.NEW)
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            when(productJpaRepository.findVisibleByCategoryId(
                    1L, ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE))
                    .thenReturn(List.of(productEntity));

            when(productDataAccessMapper.toDomainStatus(ProductStatusEntity.ACTIVE))
                    .thenReturn(ProductStatus.ACTIVE);
            when(productDataAccessMapper.toDomainConditionType(ConditionTypeEntity.NEW))
                    .thenReturn(ConditionType.NEW);

            // When
            List<ReadProductView> result = productReadRepository.findVisibleByCategoryId(categoryId);

            // Then
            assertThat(result).hasSize(1);
            ReadProductView view = result.getFirst();
            assertThat(view.id()).isEqualTo(productEntity.getId());
            assertThat(view.categoryId()).isEqualTo(1L);
            assertThat(view.name()).isEqualTo("와이드핏 데님");
            assertThat(view.brand()).isEqualTo("브랜드A");
            assertThat(view.mainImageUrl()).isEqualTo("https://example.com/image.jpg");
            assertThat(view.basePrice()).isEqualByComparingTo(new BigDecimal("99000"));
            assertThat(view.status()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(view.conditionType()).isEqualTo(ConditionType.NEW);

            verify(productJpaRepository).findVisibleByCategoryId(
                    1L, ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE);
        }

        @Test
        @DisplayName("null 카테고리 ID로 조회 시 예외 발생")
        void findVisibleByCategoryId_Null_ThrowsException() {
            assertThatThrownBy(() -> productReadRepository.findVisibleByCategoryId(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("categoryId must not be null");

            verifyNoInteractions(productJpaRepository);
        }
    }

    @Nested
    @DisplayName("findVisibleProductDetailById 테스트")
    class FindVisibleByIdTests {

        @Test
        @DisplayName("ID로 보이는 상품 상세 조회 성공")
        void findVisibleProductDetailById_Success() {
            // Given
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);
            Instant now = Instant.now();

            ProductEntity productEntity = ProductEntity.builder()
                    .id(rawId)
                    .category(null)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .basePrice(new BigDecimal("99000"))
                    .status(ProductStatusEntity.ACTIVE)
                    .conditionType(ConditionTypeEntity.NEW)
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            ProductOptionValueEntity optionValue = ProductOptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .optionValueId(UUID.randomUUID())
                    .priceDelta(new BigDecimal("1500"))
                    .isDefault(true)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            ProductOptionGroupEntity optionGroup = ProductOptionGroupEntity.builder()
                    .id(UUID.randomUUID())
                    .optionGroupId(UUID.randomUUID())
                    .stepOrder(1)
                    .isRequired(true)
                    .build();
            optionGroup.addOptionValue(optionValue);
            productEntity.addOptionGroup(optionGroup);

            VariantOptionValueEntity selected = VariantOptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .productOptionValueId(optionValue.getId())
                    .build();
            ProductVariantEntity variant = ProductVariantEntity.builder()
                    .id(UUID.randomUUID())
                    .sku("SKU-001")
                    .stockQuantity(3)
                    .status(ProductStatusEntity.ACTIVE)
                    .calculatedPrice(new BigDecimal("100500"))
                    .build();
            variant.addSelectedOptionValue(selected);
            productEntity.addVariant(variant);

            when(productJpaRepository.findVisibleDetailWithOptionsById(
                    rawId, ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE))
                    .thenReturn(Optional.of(productEntity));
            when(productJpaRepository.findVisibleDetailWithVariantsById(
                    rawId, ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE))
                    .thenReturn(Optional.of(productEntity));

            when(productDataAccessMapper.toDomainStatus(ProductStatusEntity.ACTIVE))
                    .thenReturn(ProductStatus.ACTIVE);
            when(productDataAccessMapper.toDomainConditionType(ConditionTypeEntity.NEW))
                    .thenReturn(ConditionType.NEW);

            // When
            Optional<ReadProductDetailView> result = productReadRepository.findVisibleProductDetailById(productId);

            // Then
            assertThat(result).isPresent();
            ReadProductDetailView view = result.get();
            assertThat(view.id()).isEqualTo(rawId);
            assertThat(view.categoryId()).isNull();
            assertThat(view.status()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(view.conditionType()).isEqualTo(ConditionType.NEW);
            assertThat(view.optionGroups()).hasSize(1);
            assertThat(view.optionGroups().getFirst().optionValues()).hasSize(1);
            assertThat(view.variants()).hasSize(1);
            assertThat(view.variants().getFirst().selectedProductOptionValueIds()).containsExactlyInAnyOrder(optionValue.getId());

            verify(productJpaRepository).findVisibleDetailWithOptionsById(
                    rawId, ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE);
            verify(productJpaRepository).findVisibleDetailWithVariantsById(
                    rawId, ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회 시 빈 Optional 반환")
        void findVisibleProductDetailById_NotFound_ReturnsEmpty() {
            // Given
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);

            when(productJpaRepository.findVisibleDetailWithOptionsById(
                    rawId, ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE))
                    .thenReturn(Optional.empty());

            // When
            Optional<ReadProductDetailView> result = productReadRepository.findVisibleProductDetailById(productId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null ID로 조회 시 예외 발생")
        void findVisibleProductDetailById_Null_ThrowsException() {
            assertThatThrownBy(() -> productReadRepository.findVisibleProductDetailById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("productId must not be null");

            verifyNoInteractions(productJpaRepository);
        }
    }
}

