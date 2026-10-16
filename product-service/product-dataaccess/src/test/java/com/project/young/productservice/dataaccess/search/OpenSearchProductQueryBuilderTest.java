package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.application.dto.condition.ProductSearchCriteria;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchProductQueryBuilderTest {

    private final StubQueryBuilder builder = new StubQueryBuilder();

    @Test
    void appliesSubclassQueryAlongsideAllCommonFilters() {
        var criteria = new ProductSearchCriteria("이어폰", 12L, List.of("apple", "samsung"),
                new BigDecimal("10.00"), new BigDecimal("100.00"), PublicProductSort.RELEVANCE, 0, 24);
        var request = builder.build(criteria, "pit", 3, Map.of(), true);
        var query = request.query().bool();
        assertThat(builder.keyword).isEqualTo("이어폰");
        assertThat(query.must()).containsExactly(builder.textQuery);
        assertThat(query.filter()).hasSize(6);
        assertThat(query.filter().get(1).term().field()).isEqualTo("productStatus");
        assertThat(query.filter().get(1).term().value().stringValue()).isEqualTo("ACTIVE");
        assertThat(query.filter().get(2).term().field()).isEqualTo("categoryStatus");
        assertThat(query.filter().get(2).term().value().stringValue()).isEqualTo("ACTIVE");
        assertThat(query.filter().get(3).term().value().longValue()).isEqualTo(12);
        assertThat(query.filter().get(4).terms().terms().value()).extracting(FieldValue::stringValue)
                .containsExactly("apple", "samsung");
        assertThat(query.filter().get(5).range().gte().to(BigDecimal.class)).isEqualByComparingTo("10.00");
        assertThat(query.filter().get(5).range().lte().to(BigDecimal.class)).isEqualByComparingTo("100.00");
    }

    @Test
    void browsingSkipsTextHookAndRetainsActiveFilters() {
        var request = builder.build(criteria(null, PublicProductSort.NEWEST), "pit", 3, Map.of(), true);
        assertThat(builder.keyword).isNull();
        assertThat(request.query().bool().must()).isEmpty();
        assertThat(request.query().bool().filter()).hasSize(3);
    }

    @Test
    void includesRequestedProductPageAndPriceOnFirstCall() {
        var request = builder.build(criteria(null, PublicProductSort.NEWEST), "pit", 3, Map.of(), true);
        assertThat(request.from()).isEqualTo(72);
        assertThat(request.size()).isEqualTo(24);
        assertThat(request.pit().id()).isEqualTo("pit");
        assertThat(request.pit().keepAlive()).isEqualTo("1m");
        assertThat(request.timeout()).isEqualTo("3s");
        assertThat(request.allowPartialSearchResults()).isFalse();
        assertThat(request.trackTotalHits().enabled()).isTrue();
        assertThat(request.aggregations().get("price").stats().field()).isEqualTo("basePrice");
        var brands = request.aggregations().get(OpenSearchProductQueryBuilder.BRANDS);
        assertThat(brands.composite().size()).isEqualTo(100);
        assertThat(brands.composite().sources().getFirst().get("brand").terms().field()).isEqualTo("brandKey");
        assertThat(brands.aggregations().get(OpenSearchProductQueryBuilder.LABEL).terms().field()).isEqualTo("brandLabel");
    }

    @Test
    void subsequentCallKeepsQueryAndCursorWithoutProductsPriceOrSort() {
        var after = Map.of("brand", FieldValue.of("sony"));
        var request = builder.build(criteria("이어폰", PublicProductSort.RELEVANCE), "pit", 3, after, false);
        assertThat(request.from()).isZero();
        assertThat(request.size()).isZero();
        assertThat(request.sort()).isEmpty();
        assertThat(request.aggregations()).doesNotContainKey("price");
        assertThat(request.aggregations().get(OpenSearchProductQueryBuilder.BRANDS).composite().after()).isEqualTo(after);
        assertThat(request.query().bool().must()).containsExactly(builder.textQuery);
    }

    @ParameterizedTest
    @EnumSource(PublicProductSort.class)
    void preservesEachSortAndStableIdTieBreaker(PublicProductSort sort) {
        var request = builder.build(criteria("이어폰", sort), "pit", 3, Map.of(), true);
        switch (sort) {
            case RELEVANCE -> {
                assertThat(request.sort().getFirst().score().order()).isEqualTo(SortOrder.Desc);
                assertThat(request.sort().get(1).field().field()).isEqualTo("createdAt");
            }
            case NEWEST -> assertThat(request.sort().getFirst().field().field()).isEqualTo("createdAt");
            case PRICE_ASC, PRICE_DESC -> {
                assertThat(request.sort().getFirst().field().field()).isEqualTo("basePrice");
                assertThat(request.sort().getFirst().field().order())
                        .isEqualTo(sort == PublicProductSort.PRICE_ASC ? SortOrder.Asc : SortOrder.Desc);
            }
        }
        assertThat(request.sort().getLast().field().field()).isEqualTo("id");
        assertThat(request.sort().getLast().field().order()).isEqualTo(SortOrder.Desc);
    }

    private static ProductSearchCriteria criteria(String keyword, PublicProductSort sort) {
        return new ProductSearchCriteria(keyword, null, List.of(), null, null, sort, 3, 24);
    }

    private static class StubQueryBuilder extends OpenSearchProductQueryBuilder {
        private final Query textQuery = Query.of(q -> q.match(m -> m.field("name").query(FieldValue.of("stub"))));
        private String keyword;

        @Override
        protected Query buildTextQuery(String keyword) {
            this.keyword = keyword;
            return textQuery;
        }
    }
}
