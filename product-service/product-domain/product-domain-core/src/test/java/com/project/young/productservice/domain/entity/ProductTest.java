package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
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
}

