package com.project.young.productservice.application.policy;

import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StorefrontProductVisibilityPolicy")
class StorefrontProductVisibilityPolicyTest {

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"ACTIVE"})
    @DisplayName("isListedInCatalog — ACTIVE only")
    void isListedInCatalog_activeOnly(ProductStatus status) {
        assertThat(StorefrontProductVisibilityPolicy.isListedInCatalog(status)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = ProductStatus.class,
            names = {"DRAFT", "INACTIVE", "DELETED", "DISCONTINUED", "OUT_OF_STOCK"}
    )
    @DisplayName("isListedInCatalog — non-ACTIVE excluded from PLP")
    void isListedInCatalog_nonActive(ProductStatus status) {
        assertThat(StorefrontProductVisibilityPolicy.isListedInCatalog(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = ProductStatus.class,
            names = {"ACTIVE", "INACTIVE", "OUT_OF_STOCK", "DISCONTINUED"}
    )
    @DisplayName("isDetailViewable — published storefront statuses")
    void isDetailViewable_viewable(ProductStatus status) {
        assertThat(StorefrontProductVisibilityPolicy.isDetailViewable(status)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"DRAFT", "DELETED"})
    @DisplayName("isDetailViewable — draft and deleted are 404")
    void isDetailViewable_notViewable(ProductStatus status) {
        assertThat(StorefrontProductVisibilityPolicy.isDetailViewable(status)).isFalse();
    }

    @Test
    @DisplayName("isDetailViewable — null is false")
    void isDetailViewable_null() {
        assertThat(StorefrontProductVisibilityPolicy.isDetailViewable(null)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"ACTIVE"})
    @DisplayName("isPurchasable — ACTIVE only")
    void isPurchasable_activeOnly(ProductStatus status) {
        assertThat(StorefrontProductVisibilityPolicy.isPurchasable(status)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = ProductStatus.class,
            names = {"DRAFT", "INACTIVE", "DELETED", "DISCONTINUED", "OUT_OF_STOCK"}
    )
    @DisplayName("isPurchasable — preview and display-only statuses")
    void isPurchasable_notPurchasable(ProductStatus status) {
        assertThat(StorefrontProductVisibilityPolicy.isPurchasable(status)).isFalse();
    }
}
