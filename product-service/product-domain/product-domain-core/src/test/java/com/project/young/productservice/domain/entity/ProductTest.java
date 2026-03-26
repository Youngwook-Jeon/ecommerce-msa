package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductOptionGroupId;
import com.project.young.common.domain.valueobject.ProductOptionValueId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ProductTest {

    @Test
    @DisplayName("builder: name이 blank면 예외")
    void builder_BlankName_Throws() {
        assertThatThrownBy(() -> Product.builder()
                        .name(" ")
                        .description("적당히 긴 유효한 설명입니다. 20자 이상.")
                        .basePrice(new Money(new BigDecimal("10000")))
                        .brand("브랜드")
                        .mainImageUrl("https://example.com/image.jpg")
                        .conditionType(ConditionType.NEW)
                        .build())
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("Product name cannot be null or blank");
    }

    @Test
    @DisplayName("builder: description 길이가 20 미만이면 예외")
    void builder_InvalidDescriptionLength_Throws() {
        assertThatThrownBy(() -> Product.builder()
                        .name("상품")
                        .description("짧은 설명")
                        .basePrice(new Money(new BigDecimal("10000")))
                        .brand("브랜드")
                        .mainImageUrl("https://example.com/image.jpg")
                        .conditionType(ConditionType.NEW)
                        .build())
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("at least 20 characters");
    }

    @Test
    @DisplayName("builder: basePrice가 null이거나 0 이하면 예외")
    void builder_InvalidBasePrice_Throws() {
        // null
        assertThatThrownBy(() -> Product.builder()
                        .name("상품")
                        .description("적당히 긴 유효한 설명입니다. 20자 이상.")
                        .basePrice(null)
                        .brand("브랜드")
                        .mainImageUrl("https://example.com/image.jpg")
                        .conditionType(ConditionType.NEW)
                        .build())
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("Product base price cannot be null");

        // 0 이하
        assertThatThrownBy(() -> Product.builder()
                        .name("상품")
                        .description("적당히 긴 유효한 설명입니다. 20자 이상.")
                        .basePrice(new Money(new BigDecimal("0.00")))
                        .brand("브랜드")
                        .mainImageUrl("https://example.com/image.jpg")
                        .conditionType(ConditionType.NEW)
                        .build())
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("must be greater than zero");
    }

    @Test
    @DisplayName("builder: brand가 blank이면 예외")
    void builder_BlankBrand_Throws() {
        assertThatThrownBy(() -> Product.builder()
                        .name("상품")
                        .description("적당히 긴 유효한 설명입니다. 20자 이상.")
                        .basePrice(new Money(new BigDecimal("10000")))
                        .brand(" ")
                        .mainImageUrl("https://example.com/image.jpg")
                        .conditionType(ConditionType.NEW)
                        .build())
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("Product brand cannot be null or blank");
    }

    @Test
    @DisplayName("builder: mainImageUrl이 blank이면 예외")
    void builder_BlankMainImageUrl_Throws() {
        assertThatThrownBy(() -> Product.builder()
                        .name("상품")
                        .description("적당히 긴 유효한 설명입니다. 20자 이상.")
                        .basePrice(new Money(new BigDecimal("10000")))
                        .brand("브랜드")
                        .mainImageUrl(" ")
                        .conditionType(ConditionType.NEW)
                        .build())
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("Main image URL cannot be null or blank");
    }

    @Test
    @DisplayName("builder: status가 null이면 예외")
    void builder_NullStatus_Throws() {
        assertThatThrownBy(() -> Product.builder()
                        .name("상품")
                        .description("적당히 긴 유효한 설명입니다. 20자 이상.")
                        .basePrice(new Money(new BigDecimal("10000")))
                        .brand("브랜드")
                        .mainImageUrl("https://example.com/image.jpg")
                        .conditionType(ConditionType.NEW)
                        .status(null)
                        .build())
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("Product status cannot be null");
    }

    @Test
    @DisplayName("builder: 초기 상태가 DELETED이면 예외")
    void builder_InitialStatusDeleted_Throws() {
        assertThatThrownBy(() -> Product.builder()
                        .name("상품")
                        .description("적당히 긴 유효한 설명입니다. 20자 이상.")
                        .basePrice(new Money(new BigDecimal("10000")))
                        .brand("브랜드")
                        .mainImageUrl("https://example.com/image.jpg")
                        .conditionType(ConditionType.NEW)
                        .status(ProductStatus.DELETED)
                        .build())
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("must not be created with DELETED status");
    }

    @Test
    @DisplayName("changeName: 삭제된 상품은 이름 변경 불가")
    void changeName_Deleted_Throws() {
        Product product = Product.reconstitute(
                new ProductId(UUID.randomUUID()),
                null,
                "상품",
                "적당히 긴 유효한 설명입니다. 20자 이상.",
                new Money(new BigDecimal("10000")),
                ProductStatus.DELETED,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        assertThatThrownBy(() -> product.changeName("새이름"))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("deleted product");
    }

    @Test
    @DisplayName("changeName: 유효한 이름으로 변경 성공")
    void changeName_Success() {
        Product product = Product.reconstitute(
                new ProductId(UUID.randomUUID()),
                null,
                "상품",
                "적당히 긴 유효한 설명입니다. 20자 이상.",
                new Money(new BigDecimal("10000")),
                ProductStatus.ACTIVE,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        product.changeName("새이름");
        assertThat(product.getName()).isEqualTo("새이름");
    }

    @Test
    @DisplayName("changeStatus: 삭제된 상품은 상태 변경 불가")
    void changeStatus_FromDeleted_Throws() {
        Product product = Product.reconstitute(
                new ProductId(UUID.randomUUID()),
                null,
                "상품",
                "적당히 긴 유효한 설명입니다. 20자 이상.",
                new Money(new BigDecimal("10000")),
                ProductStatus.DELETED,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        assertThatThrownBy(() -> product.changeStatus(ProductStatus.ACTIVE))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("deleted product");
    }

    @Test
    @DisplayName("markAsDeleted: 멱등적으로 DELETED 설정")
    void markAsDeleted_Idempotent() {
        Product product = Product.reconstitute(
                new ProductId(UUID.randomUUID()),
                null,
                "상품",
                "적당히 긴 유효한 설명입니다. 20자 이상.",
                new Money(new BigDecimal("10000")),
                ProductStatus.ACTIVE,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        product.markAsDeleted();
        product.markAsDeleted();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELETED);
        assertThat(product.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("changeCategoryId: 삭제된 상품은 카테고리 변경 불가")
    void changeCategoryId_Deleted_Throws() {
        Product product = Product.reconstitute(
                new ProductId(UUID.randomUUID()),
                new CategoryId(1L),
                "상품",
                "적당히 긴 유효한 설명입니다. 20자 이상.",
                new Money(new BigDecimal("10000")),
                ProductStatus.DELETED,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        assertThatThrownBy(() -> product.changeCategoryId(new CategoryId(2L)))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("deleted product");
    }

    @Test
    @DisplayName("changeCategoryId: null을 넘기면 카테고리 제거")
    void changeCategoryId_Null_AllowsRemovingCategory() {
        Product product = Product.reconstitute(
                new ProductId(UUID.randomUUID()),
                new CategoryId(1L),
                "상품",
                "적당히 긴 유효한 설명입니다. 20자 이상.",
                new Money(new BigDecimal("10000")),
                ProductStatus.ACTIVE,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        product.changeCategoryId(null);
        assertThat(product.getCategoryId()).isEmpty();
    }

    @Nested
    @DisplayName("하위 엔티티 동작")
    class SubAggregateTests {
        @Test
        @DisplayName("addOptionGroup/addVariant 후 basePrice 변경 시 variant 계산가가 재계산된다")
        void recalculateVariantPrice_WhenBasePriceChanges() {
            Product product = createBaseProduct();
            ProductOptionValue pov = ProductOptionValue.reconstitute(
                    new ProductOptionValueId(UUID.randomUUID()),
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
            product.addOptionGroup(pog);

            ProductVariant variant = ProductVariant.reconstitute(
                    new ProductVariantId(UUID.randomUUID()),
                    "SKU-001",
                    3,
                    ProductStatus.ACTIVE,
                    Money.ZERO,
                    Set.of(pov.getId())
            );
            product.addVariant(variant);

            assertThat(variant.getCalculatedPrice().getAmount()).isEqualByComparingTo("11000.00");

            product.changeBasePrice(new Money(new BigDecimal("15000")));

            assertThat(variant.getCalculatedPrice().getAmount()).isEqualByComparingTo("16000.00");
        }

        @Test
        @DisplayName("updateVariantDetails: 재고/상태 변경 성공")
        void updateVariantDetails_Success() {
            Product product = createBaseProduct();
            ProductVariantId variantId = new ProductVariantId(UUID.randomUUID());
            ProductVariant variant = ProductVariant.reconstitute(
                    variantId, "SKU-001", 5, ProductStatus.ACTIVE, new Money(new BigDecimal("10000")), Set.of()
            );
            product.addVariant(variant);

            ProductVariant updated = product.updateVariantDetails(variantId, 0, ProductStatus.INACTIVE);

            assertThat(updated.getStockQuantity()).isEqualTo(0);
            assertThat(updated.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        }

        @Test
        @DisplayName("deleteVariant: soft delete 처리한다")
        void deleteVariant_SoftDelete() {
            Product product = createBaseProduct();
            ProductVariantId variantId = new ProductVariantId(UUID.randomUUID());
            ProductVariant variant = ProductVariant.reconstitute(
                    variantId, "SKU-001", 5, ProductStatus.ACTIVE, new Money(new BigDecimal("10000")), Set.of()
            );
            product.addVariant(variant);

            ProductVariant deleted = product.deleteVariant(variantId);

            assertThat(deleted.getStatus()).isEqualTo(ProductStatus.DELETED);
        }

        @Test
        @DisplayName("deactivateProductOptionValue: 옵션값을 비활성화한다")
        void deactivateProductOptionValue_Success() {
            Product product = createBaseProduct();
            ProductOptionValueId optionValueId = new ProductOptionValueId(UUID.randomUUID());
            ProductOptionValue pov = ProductOptionValue.reconstitute(
                    optionValueId,
                    new OptionValueId(UUID.randomUUID()),
                    new Money(new BigDecimal("500")),
                    false,
                    true
            );
            ProductOptionGroup pog = ProductOptionGroup.reconstitute(
                    new ProductOptionGroupId(UUID.randomUUID()),
                    new OptionGroupId(UUID.randomUUID()),
                    1,
                    false,
                    List.of(pov)
            );
            product.addOptionGroup(pog);

            ProductOptionValue deactivated = product.deactivateProductOptionValue(optionValueId);

            assertThat(deactivated.isActive()).isFalse();
        }

        @Test
        @DisplayName("markAsDeleted 시 하위 variant들도 DELETED로 전파된다")
        void markAsDeleted_PropagatesToVariants() {
            Product product = createBaseProduct();
            ProductVariant v1 = ProductVariant.reconstitute(
                    new ProductVariantId(UUID.randomUUID()), "SKU-001", 1, ProductStatus.ACTIVE, new Money(new BigDecimal("10000")), Set.of()
            );
            ProductVariant v2 = ProductVariant.reconstitute(
                    new ProductVariantId(UUID.randomUUID()), "SKU-002", 2, ProductStatus.INACTIVE, new Money(new BigDecimal("10000")), Set.of()
            );
            product.addVariant(v1);
            product.addVariant(v2);

            product.markAsDeleted();

            assertThat(product.getStatus()).isEqualTo(ProductStatus.DELETED);
            assertThat(v1.getStatus()).isEqualTo(ProductStatus.DELETED);
            assertThat(v2.getStatus()).isEqualTo(ProductStatus.DELETED);
        }
    }

    private Product createBaseProduct() {
        return Product.reconstitute(
                new ProductId(UUID.randomUUID()),
                null,
                "상품",
                "적당히 긴 유효한 설명입니다. 20자 이상.",
                new Money(new BigDecimal("10000")),
                ProductStatus.ACTIVE,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}

