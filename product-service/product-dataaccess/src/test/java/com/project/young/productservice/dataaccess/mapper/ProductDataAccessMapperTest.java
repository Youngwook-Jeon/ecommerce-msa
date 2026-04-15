package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductOptionGroupId;
import com.project.young.common.domain.valueobject.ProductOptionValueId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.entity.VariantOptionValueEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.entity.ProductOptionGroup;
import com.project.young.productservice.domain.entity.ProductOptionValue;
import com.project.young.productservice.domain.entity.ProductVariant;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ProductDataAccessMapperTest {

    private final ProductDataAccessMapper mapper = new ProductDataAccessMapper();

    @Nested
    @DisplayName("Domain -> Entity")
    class DomainToEntity {
        @Test
        @DisplayName("하위 aggregate를 포함해 엔티티로 매핑한다")
        void productToProductEntity_withChildren_success() {
            UUID productId = UUID.randomUUID();
            UUID optionGroupId = UUID.randomUUID();
            UUID optionValueRefId = UUID.randomUUID();
            UUID productOptionGroupId = UUID.randomUUID();
            UUID productOptionValueId = UUID.randomUUID();
            UUID variantId = UUID.randomUUID();

            ProductOptionValue optionValue = ProductOptionValue.reconstitute(
                    new ProductOptionValueId(productOptionValueId),
                    new OptionValueId(optionValueRefId),
                    new Money(new BigDecimal("3000")),
                    true,
                    true
            );
            ProductOptionGroup optionGroup = ProductOptionGroup.reconstitute(
                    new ProductOptionGroupId(productOptionGroupId),
                    new OptionGroupId(optionGroupId),
                    1,
                    true,
                    List.of(optionValue)
            );
            ProductVariant variant = ProductVariant.reconstitute(
                    new ProductVariantId(variantId),
                    "SKU-NEW-001",
                    5,
                    ProductStatus.ACTIVE,
                    new Money(new BigDecimal("103000")),
                    Set.of(new ProductOptionValueId(productOptionValueId))
            );

            Product domain = Product.reconstitute(
                    new ProductId(productId),
                    new CategoryId(1L),
                    "와이드핏 데님",
                    "desc",
                    new Money(new BigDecimal("100000")),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW,
                    "브랜드A",
                    "https://example.com/image.jpg",
                    List.of(optionGroup),
                    List.of(variant)
            );

            ProductEntity entity = mapper.productToProductEntity(domain, category(1L, "의류"));

            assertThat(entity.getId()).isEqualTo(productId);
            assertThat(entity.getOptionGroups()).hasSize(1);
            assertThat(entity.getVariants()).hasSize(1);

            ProductOptionGroupEntity mappedGroup = entity.getOptionGroups().iterator().next();
            assertThat(mappedGroup.getProduct()).isSameAs(entity);
            assertThat(mappedGroup.getOptionValues()).hasSize(1);

            ProductVariantEntity mappedVariant = entity.getVariants().iterator().next();
            assertThat(mappedVariant.getProduct()).isSameAs(entity);
            assertThat(mappedVariant.getSelectedOptionValues()).hasSize(1);
            assertThat(mappedVariant.getSelectedOptionValues().iterator().next().getVariant()).isSameAs(mappedVariant);
        }
    }

    @Nested
    @DisplayName("Merge Update")
    class MergeUpdate {
        @Test
        @DisplayName("기존 자식 엔티티를 재사용하면서 값만 갱신하고 선택옵션은 remove/add 동기화한다")
        void updateEntityFromDomain_mergeChildren_success() {
            UUID productId = UUID.randomUUID();
            UUID keepGroupId = UUID.randomUUID();
            UUID keepValueId = UUID.randomUUID();
            UUID keepVariantId = UUID.randomUUID();
            UUID selectedKeep = UUID.randomUUID();
            UUID selectedRemove = UUID.randomUUID();
            UUID selectedAdd = UUID.randomUUID();

            ProductOptionValueEntity existingValue = ProductOptionValueEntity.builder()
                    .id(keepValueId)
                    .optionValueId(UUID.randomUUID())
                    .priceDelta(new BigDecimal("1000"))
                    .isDefault(false)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();

            ProductOptionGroupEntity existingGroup = ProductOptionGroupEntity.builder()
                    .id(keepGroupId)
                    .optionGroupId(UUID.randomUUID())
                    .stepOrder(9)
                    .isRequired(false)
                    .optionValues(new java.util.HashSet<>(Set.of(existingValue)))
                    .build();
            existingGroup.addOptionValue(existingValue);

            VariantOptionValueEntity keepSelection = VariantOptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .productOptionValueId(selectedKeep)
                    .build();
            VariantOptionValueEntity removeSelection = VariantOptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .productOptionValueId(selectedRemove)
                    .build();

            ProductVariantEntity existingVariant = ProductVariantEntity.builder()
                    .id(keepVariantId)
                    .sku("OLD-SKU")
                    .stockQuantity(1)
                    .status(ProductStatusEntity.ACTIVE)
                    .calculatedPrice(new BigDecimal("101000"))
                    .selectedOptionValues(new java.util.HashSet<>(Set.of(keepSelection, removeSelection)))
                    .build();
            existingVariant.addSelectedOptionValue(keepSelection);
            existingVariant.addSelectedOptionValue(removeSelection);

            ProductEntity targetEntity = ProductEntity.builder()
                    .id(productId)
                    .name("old-name")
                    .description("old-desc")
                    .basePrice(new BigDecimal("100000"))
                    .status(ProductStatusEntity.ACTIVE)
                    .conditionType(ConditionTypeEntity.NEW)
                    .brand("old-brand")
                    .mainImageUrl("old-url")
                    .optionGroups(new java.util.HashSet<>(Set.of(existingGroup)))
                    .variants(new java.util.HashSet<>(Set.of(existingVariant)))
                    .build();
            targetEntity.addOptionGroup(existingGroup);
            targetEntity.addVariant(existingVariant);

            ProductOptionValue domainValue = ProductOptionValue.reconstitute(
                    new ProductOptionValueId(keepValueId),
                    new OptionValueId(UUID.randomUUID()),
                    new Money(new BigDecimal("7000")),
                    true,
                    true
            );
            ProductOptionGroup domainGroup = ProductOptionGroup.reconstitute(
                    new ProductOptionGroupId(keepGroupId),
                    new OptionGroupId(UUID.randomUUID()),
                    1,
                    true,
                    List.of(domainValue)
            );
            ProductVariant domainVariant = ProductVariant.reconstitute(
                    new ProductVariantId(keepVariantId),
                    "NEW-SKU",
                    20,
                    ProductStatus.INACTIVE,
                    new Money(new BigDecimal("107000")),
                    Set.of(new ProductOptionValueId(selectedKeep), new ProductOptionValueId(selectedAdd))
            );
            Product domain = Product.reconstitute(
                    new ProductId(productId),
                    new CategoryId(2L),
                    "new-name",
                    "new-desc",
                    new Money(new BigDecimal("110000")),
                    ProductStatus.INACTIVE,
                    ConditionType.USED,
                    "new-brand",
                    "new-url",
                    List.of(domainGroup),
                    List.of(domainVariant)
            );

            mapper.updateEntityFromDomain(domain, targetEntity, category(2L, "하의"));

            assertThat(targetEntity.getName()).isEqualTo("new-name");
            assertThat(targetEntity.getStatus()).isEqualTo(ProductStatusEntity.INACTIVE);
            assertThat(targetEntity.getConditionType()).isEqualTo(ConditionTypeEntity.USED);

            ProductOptionGroupEntity mergedGroup = targetEntity.getOptionGroups().stream()
                    .filter(g -> g.getId().equals(keepGroupId))
                    .findFirst()
                    .orElseThrow();
            assertThat(mergedGroup.getStepOrder()).isEqualTo(1);
            assertThat(mergedGroup.isRequired()).isTrue();

            ProductOptionValueEntity mergedValue = mergedGroup.getOptionValues().stream()
                    .filter(v -> v.getId().equals(keepValueId))
                    .findFirst()
                    .orElseThrow();
            assertThat(mergedValue.getPriceDelta()).isEqualByComparingTo(new BigDecimal("7000"));
            assertThat(mergedValue.isDefault()).isTrue();

            ProductVariantEntity mergedVariant = targetEntity.getVariants().stream()
                    .filter(v -> v.getId().equals(keepVariantId))
                    .findFirst()
                    .orElseThrow();
            assertThat(mergedVariant.getSku()).isEqualTo("NEW-SKU");
            assertThat(mergedVariant.getStockQuantity()).isEqualTo(20);
            assertThat(mergedVariant.getStatus()).isEqualTo(ProductStatusEntity.INACTIVE);
            assertThat(mergedVariant.getSelectedOptionValues().stream()
                    .map(VariantOptionValueEntity::getProductOptionValueId))
                    .containsExactlyInAnyOrder(selectedKeep, selectedAdd);
        }
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

    @Test
    @DisplayName("null 입력 검증: productToProductEntity/updateEntityFromDomain")
    void nullGuards() {
        assertThatThrownBy(() -> mapper.productToProductEntity(null, category(1L, "x")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("product");
        assertThatThrownBy(() -> mapper.updateEntityFromDomain(null, ProductEntity.builder().build(), category(1L, "x")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("domainProduct");
        assertThatThrownBy(() -> mapper.updateEntityFromDomain(
                Product.reconstitute(
                        new ProductId(UUID.randomUUID()),
                        new CategoryId(1L),
                        "n",
                        "d",
                        new Money(BigDecimal.ONE),
                        ProductStatus.ACTIVE,
                        ConditionType.NEW,
                        "b",
                        "u",
                        List.of(),
                        List.of()
                ),
                null,
                category(1L, "x")
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("productEntity");
    }

    private CategoryEntity category(Long id, String name) {
        return CategoryEntity.builder()
                .id(id)
                .name(name)
                .build();
    }
}

