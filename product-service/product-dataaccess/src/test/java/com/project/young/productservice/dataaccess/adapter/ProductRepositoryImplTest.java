package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.mapper.ProductAggregateMapper;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import jakarta.persistence.EntityManager;
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
class ProductRepositoryImplTest {

    @Mock
    private ProductJpaRepository productJpaRepository;

    @Mock
    private CategoryJpaRepository categoryJpaRepository;

    @Mock
    private ProductDataAccessMapper productDataAccessMapper;

    @Mock
    private ProductAggregateMapper productAggregateMapper;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ProductRepositoryImpl productRepository;

    @Nested
    @DisplayName("insert/update 테스트")
    class SaveTests {

        @Test
        @DisplayName("insert: null 상품 저장 시 예외 발생")
        void insert_NullProduct_ThrowsException() {
            assertThatThrownBy(() -> productRepository.insert(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("product must not be null");

            verifyNoInteractions(productJpaRepository, categoryJpaRepository, productDataAccessMapper, entityManager);
        }

        @Test
        @DisplayName("insert: 새 상품 저장 성공 (카테고리 없음)")
        void insert_NewProductWithoutCategory_Success() {
            Product product = Product.builder()
                    .productId(new ProductId(UUID.randomUUID()))
                    .categoryId(null)
                    .name("와이드핏 데님")
                    .description("브랜드A의 와이드핏 데님 상세 설명입니다.")
                    .basePrice(new Money(new BigDecimal("99000")))
                    .status(ProductStatus.ACTIVE)
                    .conditionType(ConditionType.NEW)
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .build();

            ProductEntity toPersistEntity = mock(ProductEntity.class);

            when(productDataAccessMapper.productToProductEntity(eq(product), isNull()))
                    .thenReturn(toPersistEntity);

            productRepository.insert(product);

            verify(productDataAccessMapper).productToProductEntity(eq(product), isNull());
            verify(entityManager).persist(toPersistEntity);
            verify(productJpaRepository, never()).save(any());
        }

        @Test
        @DisplayName("update: 기존 상품 업데이트 성공 (카테고리 포함)")
        void update_ExistingProduct_Success() {
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);
            CategoryId categoryId = new CategoryId(10L);

            Product domainProduct = Product.reconstitute(
                    productId,
                    categoryId,
                    "업데이트된 이름",
                    "업데이트된 설명입니다.",
                    new Money(new BigDecimal("150000")),
                    ProductStatus.ACTIVE,
                    ConditionType.USED,
                    "브랜드B",
                    "https://example.com/updated.jpg",
                    new ArrayList<>(),
                    new ArrayList<>()
            );

            ProductEntity existingEntity = mock(ProductEntity.class);
            CategoryEntity categoryRef = mock(CategoryEntity.class);

            when(productJpaRepository.findById(rawId)).thenReturn(Optional.of(existingEntity));
            when(categoryJpaRepository.getReferenceById(10L)).thenReturn(categoryRef);

            productRepository.update(domainProduct);

            verify(productJpaRepository).findById(rawId);
            verify(categoryJpaRepository).getReferenceById(10L);
            verify(productDataAccessMapper).updateEntityFromDomain(domainProduct, existingEntity, categoryRef);
            verify(productJpaRepository, never()).save(any());
        }

        @Test
        @DisplayName("update: 존재하지 않는 상품 업데이트 시 ProductNotFoundException 발생")
        void update_NonExistingProduct_ThrowsException() {
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);

            Product domainProduct = Product.reconstitute(
                    productId,
                    null,
                    "이름",
                    "설명입니다.",
                    new Money(new BigDecimal("10000")),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW,
                    "브랜드",
                    "https://example.com/image.jpg",
                    new ArrayList<>(),
                    new ArrayList<>()
            );

            when(productJpaRepository.findById(rawId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productRepository.update(domainProduct))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("Product not found");

            verify(productJpaRepository).findById(rawId);
            verify(productJpaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findById 테스트")
    class FindByIdTests {

        @Test
        @DisplayName("ID로 상품 조회 성공")
        void findById_Success() {
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);

            ProductEntity entity = mock(ProductEntity.class);
            Product domainProduct = Product.reconstitute(
                    productId,
                    null,
                    "이름",
                    "설명입니다.",
                    new Money(new BigDecimal("10000")),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW,
                    "브랜드",
                    "https://example.com/image.jpg",
                    new ArrayList<>(),
                    new ArrayList<>()
            );

            when(productJpaRepository.findAggregateById(rawId)).thenReturn(Optional.of(entity));
            when(productAggregateMapper.toProduct(entity)).thenReturn(domainProduct);

            Optional<Product> result = productRepository.findById(productId);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("이름");
            verify(productJpaRepository).findAggregateById(rawId);
            verify(productAggregateMapper).toProduct(entity);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회 시 빈 Optional 반환")
        void findById_NotFound_ReturnsEmpty() {
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);

            when(productJpaRepository.findAggregateById(rawId)).thenReturn(Optional.empty());

            Optional<Product> result = productRepository.findById(productId);

            assertThat(result).isEmpty();
            verify(productJpaRepository).findAggregateById(rawId);
        }

        @Test
        @DisplayName("null ID로 조회 시 예외 발생")
        void findById_Null_ThrowsException() {
            assertThatThrownBy(() -> productRepository.findById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("productId must not be null");

            verifyNoInteractions(productJpaRepository);
        }
    }
}
