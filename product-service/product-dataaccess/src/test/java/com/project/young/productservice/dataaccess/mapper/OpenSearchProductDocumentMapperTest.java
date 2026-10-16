package com.project.young.productservice.dataaccess.mapper;

import com.project.young.productservice.application.dto.query.ProductSearchQuery;
import com.project.young.productservice.application.service.ProductSearchQueryValidator;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenSearchProductDocumentMapperTest {

    private final OpenSearchProductDocumentMapper mapper = new OpenSearchProductDocumentMapper();

    @Test
    void mapsPersistedFieldsWithoutChangingSourceAndKeepsOptionalFieldsNull() {
        var product = product();
        var document = mapper.toDocument(product, 1);
        assertThat(document.id()).isEqualTo(product.getId().toString());
        assertThat(document.categoryId()).isEqualTo(12);
        assertThat(document.name()).isEqualTo("상품 이름");
        assertThat(document.description()).isEqualTo(" 긴 설명\n줄바꿈 유지 ");
        assertThat(document.mainImageUrl()).isNull();
        assertThat(document.brand()).isNull();
        assertThat(document.basePrice()).isEqualTo(new BigDecimal("10.00"));
        assertThat(product.getBasePrice()).isEqualTo(new BigDecimal("10"));
        assertThat(document.productStatus()).isEqualTo("ACTIVE");
        assertThat(document.categoryStatus()).isEqualTo("ACTIVE");
        assertThat(document.createdAt()).isEqualTo("2026-10-11T00:00:00.123Z");
        product.setMainImageUrl("https://example.com/image.jpg");
        product.setDescription(null);
        var updated = mapper.toDocument(product, 1);
        assertThat(updated.id()).isEqualTo(document.id());
        assertThat(updated.mainImageUrl()).isEqualTo(product.getMainImageUrl());
        assertThat(updated.description()).isNull();
    }

    @Test
    void missingCategory_createsVersionedNonSearchableDocument() {
        var product = product();
        product.setCategory(null);
        var document = mapper.toDocument(product, 7);
        assertThat(document.documentReady()).isFalse();
        assertThat(document.catalogRevision()).isEqualTo(7);
        assertThat(document.categoryId()).isNull();
        assertThat(document.basePrice()).isNull();
    }

    @Test
    void categoryNameChanges_doNotChangeIndexedDocument() {
        var product = product();
        var before = mapper.toDocument(product, 1);
        product.getCategory().setName(null);
        assertThat(mapper.toDocument(product, 1)).isEqualTo(before);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(before).has("categoryName")).isFalse();
    }

    @Test
    void brandNormalizationMatchesRequestFiltersAndPreservesInternalSpaces() {
        var product = product();
        product.setBrand("\u00a0가 EXAMPLE  Brand\u2003");
        var document = mapper.toDocument(product, 1);
        assertThat(document.brandLabel()).isEqualTo("가 EXAMPLE  Brand");
        assertThat(document.brand()).isEqualTo(document.brandLabel());
        assertThat(document.brandKey()).isEqualTo("가 example  brand");
        var criteria = new ProductSearchQueryValidator().validate(new ProductSearchQuery(null, null,
                List.of(product.getBrand()), null, null, null, null, null));
        assertThat(criteria.brands()).containsExactly(document.brandKey());
        assertThat(product.getBrand()).isEqualTo("\u00a0가 EXAMPLE  Brand\u2003");
    }

    @Test
    void brandKeyDoesNotDependOnDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var product = product();
            product.setBrand("IKEA");
            assertThat(mapper.toDocument(product, 1).brandKey()).isEqualTo("ikea");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u00a0\u2003"})
    void absentBrandsHaveNoTextKeyOrLabel(String brand) {
        var product = product();
        product.setBrand(brand);
        var document = mapper.toDocument(product, 1);
        assertThat(document.brand()).isNull();
        assertThat(document.brandKey()).isNull();
        assertThat(document.brandLabel()).isNull();
    }

    @ParameterizedTest
    @EnumSource(ProductStatusEntity.class)
    void preservesAllProductStatusesForSearchVisibilityFiltering(ProductStatusEntity status) {
        var product = product();
        product.setStatus(status);
        assertThat(mapper.toDocument(product, 1).productStatus()).isEqualTo(status.name());
    }

    @ParameterizedTest
    @EnumSource(CategoryStatusEntity.class)
    void preservesAllCategoryStatusesForSearchVisibilityFiltering(CategoryStatusEntity status) {
        var product = product();
        product.getCategory().setStatus(status);
        assertThat(mapper.toDocument(product, 1).categoryStatus()).isEqualTo(status.name());
    }

    @Test
    void rejectsNullProduct() {
        assertThatThrownBy(() -> mapper.toDocument(null, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidProducts")
    void rejectsIncompleteOrUnrepresentableDocuments(Consumer<ProductEntity> invalidate) {
        var product = product();
        invalidate.accept(product);
        assertThatThrownBy(() -> mapper.toDocument(product, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Consumer<ProductEntity>> invalidProducts() {
        return Stream.<Consumer<ProductEntity>>of(
                p -> p.setId(null),
                p -> p.getCategory().setId(null),
                p -> p.getCategory().setId(0L),
                p -> p.setName(null),
                p -> p.setName(" "),
                p -> p.setStatus(null),
                p -> p.getCategory().setStatus(null),
                p -> p.setCreatedAt(null),
                p -> p.setBasePrice(null),
                p -> p.setBasePrice(new BigDecimal("-0.01")),
                p -> p.setBasePrice(new BigDecimal("1.001")),
                p -> p.setBrand("a".repeat(101)),
                p -> p.setBrand("İ".repeat(51)));
    }

    @Test
    void acceptsZeroPriceAndTrailingZeroPrecisionWithoutRounding() {
        var product = product();
        product.setBasePrice(new BigDecimal("0.000"));
        assertThat(mapper.toDocument(product, 1).basePrice()).isEqualTo(new BigDecimal("0.00"));
        product.setBasePrice(new BigDecimal("12.3400"));
        assertThat(mapper.toDocument(product, 1).basePrice()).isEqualTo(new BigDecimal("12.34"));
    }

    private static ProductEntity product() {
        return ProductEntity.builder().id(UUID.randomUUID())
                .category(CategoryEntity.builder().id(12L).name("이어폰").status(CategoryStatusEntity.ACTIVE).build())
                .name("상품 이름").description(" 긴 설명\n줄바꿈 유지 ").basePrice(new BigDecimal("10"))
                .status(ProductStatusEntity.ACTIVE).createdAt(Instant.parse("2026-10-11T00:00:00.123Z")).build();
    }
}
