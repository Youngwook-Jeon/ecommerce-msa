package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ProductDataAccessMapperTest {

    private final ProductDataAccessMapper mapper = new ProductDataAccessMapper();

    @Test
    @DisplayName("productEntityToProduct: 엔티티를 도메인 객체로 올바르게 매핑한다")
    void productEntityToProduct_Success() {
        // Given
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        CategoryEntity categoryEntity = CategoryEntity.builder()
                .id(1L)
                .name("의류")
                .build();

        ProductEntity entity = ProductEntity.builder()
                .id(id)
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

        // When
        Product product = mapper.productEntityToProduct(entity);

        // Then
        assertThat(product.getId()).isEqualTo(new ProductId(id));
        assertThat(product.getCategoryId()).contains(new CategoryId(1L));
        assertThat(product.getName()).isEqualTo("와이드핏 데님");
        assertThat(product.getDescription()).isEqualTo("와이드핏 데님 상세 설명입니다.");
        assertThat(product.getBasePrice()).isEqualTo(new Money(new BigDecimal("99000")));
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.getConditionType()).isEqualTo(ConditionType.NEW);
        assertThat(product.getBrand()).isEqualTo("브랜드A");
        assertThat(product.getMainImageUrl()).isEqualTo("https://example.com/image.jpg");
    }

    @Test
    @DisplayName("productToProductEntity: 도메인 객체를 엔티티로 올바르게 매핑한다")
    void productToProductEntity_Success() {
        // Given
        UUID id = UUID.randomUUID();
        Product domain = Product.reconstitute(
                new ProductId(id),
                new CategoryId(1L),
                "와이드핏 데님",
                "와이드핏 데님 상세 설명입니다.",
                new Money(new BigDecimal("99000")),
                ProductStatus.ACTIVE,
                ConditionType.NEW,
                "브랜드A",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        CategoryEntity categoryEntity = CategoryEntity.builder()
                .id(1L)
                .name("의류")
                .build();

        // When
        ProductEntity entity = mapper.productToProductEntity(domain, categoryEntity);

        // Then
        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCategory()).isEqualTo(categoryEntity);
        assertThat(entity.getName()).isEqualTo("와이드핏 데님");
        assertThat(entity.getDescription()).isEqualTo("와이드핏 데님 상세 설명입니다.");
        assertThat(entity.getBasePrice()).isEqualByComparingTo(new BigDecimal("99000"));
        assertThat(entity.getStatus()).isEqualTo(ProductStatusEntity.ACTIVE);
        assertThat(entity.getConditionType()).isEqualTo(ConditionTypeEntity.NEW);
        assertThat(entity.getBrand()).isEqualTo("브랜드A");
        assertThat(entity.getMainImageUrl()).isEqualTo("https://example.com/image.jpg");
    }

    @Test
    @DisplayName("updateEntityFromDomain: 기존 엔티티를 도메인 값으로 업데이트한다")
    void updateEntityFromDomain_Success() {
        // Given
        UUID id = UUID.randomUUID();

        Product domain = Product.reconstitute(
                new ProductId(id),
                new CategoryId(2L),
                "업데이트된 이름",
                "업데이트된 설명입니다.",
                new Money(new BigDecimal("150000")),
                ProductStatus.INACTIVE,
                ConditionType.USED,
                "브랜드B",
                "https://example.com/updated.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        CategoryEntity categoryEntity = CategoryEntity.builder()
                .id(2L)
                .name("하의")
                .build();

        ProductEntity entity = ProductEntity.builder()
                .id(id)
                .name("이전 이름")
                .description("이전 설명")
                .basePrice(new BigDecimal("50000"))
                .status(ProductStatusEntity.ACTIVE)
                .conditionType(ConditionTypeEntity.NEW)
                .brand("브랜드A")
                .mainImageUrl("https://example.com/old.jpg")
                .build();

        // When
        mapper.updateEntityFromDomain(domain, entity, categoryEntity);

        // Then
        assertThat(entity.getCategory()).isEqualTo(categoryEntity);
        assertThat(entity.getName()).isEqualTo("업데이트된 이름");
        assertThat(entity.getDescription()).isEqualTo("업데이트된 설명입니다.");
        assertThat(entity.getBasePrice()).isEqualByComparingTo(new BigDecimal("150000"));
        assertThat(entity.getStatus()).isEqualTo(ProductStatusEntity.INACTIVE);
        assertThat(entity.getConditionType()).isEqualTo(ConditionTypeEntity.USED);
        assertThat(entity.getBrand()).isEqualTo("브랜드B");
        assertThat(entity.getMainImageUrl()).isEqualTo("https://example.com/updated.jpg");
    }

    @Test
    @DisplayName("Enum 매핑: 도메인 <-> 엔티티 변환이 name 기반으로 동작한다")
    void enumMapping_Success() {
        // ProductStatus
        assertThat(mapper.toEntityStatus(ProductStatus.ACTIVE)).isEqualTo(ProductStatusEntity.ACTIVE);
        assertThat(mapper.toDomainStatus(ProductStatusEntity.OUT_OF_STOCK)).isEqualTo(ProductStatus.OUT_OF_STOCK);

        // ConditionType
        assertThat(mapper.toEntityConditionType(ConditionType.REFURBISHED)).isEqualTo(ConditionTypeEntity.REFURBISHED);
        assertThat(mapper.toDomainConditionType(ConditionTypeEntity.OPEN_BOX)).isEqualTo(ConditionType.OPEN_BOX);
    }
}

