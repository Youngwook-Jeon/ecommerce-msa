package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.AdminProductDetailResult;
import com.project.young.productservice.application.dto.AdminProductDetailQuery;
import com.project.young.productservice.application.dto.AdminProductSearchCondition;
import com.project.young.productservice.application.port.output.AdminProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.AdminProductJpaRepository;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProductReadRepositoryImplTest {

    @Mock
    private AdminProductJpaRepository adminProductJpaRepository;

    @Mock
    private ProductDataAccessMapper productDataAccessMapper;

    @InjectMocks
    private AdminProductReadRepositoryImpl adminProductReadRepository;

    @Test
    @DisplayName("getProductDetail: ProductEntity를 AdminProductDetailResult로 매핑한다")
    void getProductDetail_MapsEntityToDetailResult() {
        // Given
        UUID productId = UUID.randomUUID();
        Instant now = Instant.now();
        CategoryEntity category = CategoryEntity.builder()
                .id(1L)
                .name("의류")
                .status(CategoryStatusEntity.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        ProductEntity productEntity = ProductEntity.builder()
                .id(productId)
                .category(category)
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
        when(adminProductJpaRepository.findById(productId))
                .thenReturn(java.util.Optional.of(productEntity));
        when(productDataAccessMapper.toDomainStatus(ProductStatusEntity.ACTIVE))
                .thenReturn(ProductStatus.ACTIVE);
        when(productDataAccessMapper.toDomainConditionType(ConditionTypeEntity.NEW))
                .thenReturn(ConditionType.NEW);
        AdminProductDetailQuery query = AdminProductDetailQuery.builder()
                .id(productId)
                .build();
        // When
        AdminProductDetailResult result = adminProductReadRepository.getProductDetail(query);
        // Then
        assertThat(result.id()).isEqualTo(productId);
        assertThat(result.categoryId()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("와이드핏 데님");
        assertThat(result.description()).isEqualTo("와이드핏 데님 상세 설명입니다.");
        assertThat(result.brand()).isEqualTo("브랜드A");
        assertThat(result.mainImageUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(result.basePrice()).isEqualByComparingTo(new BigDecimal("99000"));
        assertThat(result.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(result.conditionType()).isEqualTo(ConditionType.NEW);
        verify(adminProductJpaRepository).findById(productId);
    }

    @Test
    @DisplayName("search: 검색 조건과 페이지 정보에 맞게 ReadProductView 목록과 메타데이터를 반환한다")
    void search_ReturnsMappedResultWithPageMetadata() {
        // Given
        AdminProductSearchCondition condition = new AdminProductSearchCondition(
                1L,
                true,
                ProductStatus.ACTIVE,
                "브랜드A",
                "데님"
        );

        Instant now = Instant.now();
        CategoryEntity category = CategoryEntity.builder()
                .id(1L)
                .name("의류")
                .status(CategoryStatusEntity.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ProductEntity productEntity = ProductEntity.builder()
                .id(UUID.randomUUID())
                .category(category)
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

        Page<ProductEntity> entityPage = new PageImpl<>(
                List.of(productEntity),
                PageRequest.of(0, 10),
                1
        );

        when(productDataAccessMapper.toEntityStatus(ProductStatus.ACTIVE))
                .thenReturn(ProductStatusEntity.ACTIVE);

        when(adminProductJpaRepository.searchAdminProducts(
                eq(true),
                eq(1L),
                eq(true),
                eq(true),
                eq(ProductStatusEntity.ACTIVE),
                eq(true),
                eq("브랜드A"),
                eq(true),
                eq("데님"),
                any()))
                .thenReturn(entityPage);

        when(productDataAccessMapper.toDomainStatus(ProductStatusEntity.ACTIVE))
                .thenReturn(ProductStatus.ACTIVE);
        when(productDataAccessMapper.toDomainConditionType(ConditionTypeEntity.NEW))
                .thenReturn(ConditionType.NEW);

        // When
        AdminProductReadRepository.AdminProductSearchResult result =
                adminProductReadRepository.search(condition, 0, 10, "createdAt", false);

        // Then
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);

        assertThat(result.content()).hasSize(1);
        ReadProductView view = result.content().getFirst();
        assertThat(view.id()).isEqualTo(productEntity.getId());
        assertThat(view.categoryId()).isEqualTo(1L);
        assertThat(view.name()).isEqualTo("와이드핏 데님");
        assertThat(view.brand()).isEqualTo("브랜드A");
        assertThat(view.mainImageUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(view.basePrice()).isEqualByComparingTo(new BigDecimal("99000"));
        assertThat(view.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(view.conditionType()).isEqualTo(ConditionType.NEW);

        verify(adminProductJpaRepository).searchAdminProducts(
                eq(true),
                eq(1L),
                eq(true),
                eq(true),
                eq(ProductStatusEntity.ACTIVE),
                eq(true),
                eq("브랜드A"),
                eq(true),
                eq("데님"),
                any()
        );
    }
}