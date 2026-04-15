package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.*;
import com.project.young.productservice.dataaccess.entity.*;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.entity.ProductOptionGroup;
import com.project.young.productservice.domain.entity.ProductVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ProductAggregateMapperTest {

    private final ProductAggregateMapper mapper = new ProductAggregateMapper();

    @Nested
    @DisplayName("Entity -> Domain (fully loaded aggregate only)")
    class EntityToDomain {
        @Test
        @DisplayName("하위 aggregate(Set)까지 도메인으로 매핑한다")
        void toProduct_withChildren_success() {
            UUID productId = UUID.randomUUID();
            UUID pogId = UUID.randomUUID();
            UUID povId = UUID.randomUUID();
            UUID variantId = UUID.randomUUID();
            UUID selectedPovId = UUID.randomUUID();

            CategoryEntity categoryEntity = category(1L, "의류");

            ProductOptionValueEntity povEntity = ProductOptionValueEntity.builder()
                    .id(povId)
                    .optionValueId(UUID.randomUUID())
                    .priceDelta(new BigDecimal("5000"))
                    .isDefault(true)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();

            ProductOptionGroupEntity pogEntity = ProductOptionGroupEntity.builder()
                    .id(pogId)
                    .optionGroupId(UUID.randomUUID())
                    .stepOrder(1)
                    .isRequired(true)
                    .optionValues(Set.of(povEntity))
                    .build();
            povEntity.setProductOptionGroup(pogEntity);

            VariantOptionValueEntity selectedEntity = VariantOptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .productOptionValueId(selectedPovId)
                    .build();

            ProductVariantEntity variantEntity = ProductVariantEntity.builder()
                    .id(variantId)
                    .sku("SKU-001")
                    .stockQuantity(10)
                    .status(ProductStatusEntity.ACTIVE)
                    .calculatedPrice(new BigDecimal("105000"))
                    .selectedOptionValues(Set.of(selectedEntity))
                    .build();
            selectedEntity.setVariant(variantEntity);

            ProductEntity productEntity = ProductEntity.builder()
                    .id(productId)
                    .category(categoryEntity)
                    .name("와이드핏 데님")
                    .description("desc")
                    .basePrice(new BigDecimal("100000"))
                    .status(ProductStatusEntity.ACTIVE)
                    .conditionType(ConditionTypeEntity.NEW)
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .optionGroups(Set.of(pogEntity))
                    .variants(Set.of(variantEntity))
                    .build();
            pogEntity.setProduct(productEntity);
            variantEntity.setProduct(productEntity);

            Product product = mapper.toProduct(productEntity);

            assertThat(product.getId()).isEqualTo(new ProductId(productId));
            assertThat(product.getCategoryId()).contains(new CategoryId(1L));
            assertThat(product.getOptionGroups()).hasSize(1);
            assertThat(product.getVariants()).hasSize(1);

            ProductOptionGroup mappedGroup = product.getOptionGroups().get(0);
            assertThat(mappedGroup.getId()).isEqualTo(new ProductOptionGroupId(pogId));
            assertThat(mappedGroup.getOptionValues()).hasSize(1);

            ProductVariant mappedVariant = product.getVariants().get(0);
            assertThat(mappedVariant.getId()).isEqualTo(new ProductVariantId(variantId));
            assertThat(mappedVariant.getSelectedOptionValues())
                    .containsExactlyInAnyOrder(new ProductOptionValueId(selectedPovId));
        }
    }

    @Test
    @DisplayName("null 엔티티면 NullPointerException")
    void toProduct_null_Throws() {
        assertThatThrownBy(() -> mapper.toProduct(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("productEntity");
    }

    private CategoryEntity category(Long id, String name) {
        return CategoryEntity.builder()
                .id(id)
                .name(name)
                .build();
    }
}
