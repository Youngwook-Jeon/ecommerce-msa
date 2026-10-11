package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.application.dto.condition.ProductSearchCriteria;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregationSource;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Pit;

import java.util.List;
import java.util.Map;

/** Fixed search-request template; subclasses supply only the text-matching query. */
public abstract class OpenSearchProductQueryBuilder {

    public static final String BRANDS = "brands";
    public static final String LABEL = "label";
    public static final int BRAND_BATCH_SIZE = 100;

    public final SearchRequest build(ProductSearchCriteria criteria, String pitId, int timeoutSeconds,
                               Map<String, FieldValue> after, boolean includeProductsAndPrice) {
        BoolQuery.Builder bool = new BoolQuery.Builder()
                .filter(q -> q.term(t -> t.field("productStatus").value(FieldValue.of("ACTIVE"))))
                .filter(q -> q.term(t -> t.field("categoryStatus").value(FieldValue.of("ACTIVE"))));
        if (criteria.categoryId() != null) {
            bool.filter(q -> q.term(t -> t.field("categoryId").value(FieldValue.of(criteria.categoryId()))));
        }
        if (!criteria.brands().isEmpty()) {
            bool.filter(q -> q.terms(t -> t.field("brandKey")
                    .terms(values -> values.value(criteria.brands().stream().map(FieldValue::of).toList()))));
        }
        if (criteria.minPrice() != null || criteria.maxPrice() != null) {
            bool.filter(q -> q.range(range -> {
                range.field("basePrice");
                if (criteria.minPrice() != null) {
                    range.gte(JsonData.of(criteria.minPrice()));
                }
                if (criteria.maxPrice() != null) {
                    range.lte(JsonData.of(criteria.maxPrice()));
                }
                return range;
            }));
        }
        if (criteria.q() != null) {
            bool.must(buildTextQuery(criteria.q()));
        }
        CompositeAggregation.Builder composite = new CompositeAggregation.Builder().size(BRAND_BATCH_SIZE)
                .sources(List.of(Map.of("brand", CompositeAggregationSource.of(s -> s.terms(t -> t.field("brandKey"))))));
        if (!after.isEmpty()) {
            composite.after(after);
        }
        SearchRequest.Builder request = new SearchRequest.Builder()
                .pit(Pit.of(p -> p.id(pitId).keepAlive("1m")))
                .allowPartialSearchResults(false).timeout(timeoutSeconds + "s")
                .query(Query.of(q -> q.bool(bool.build())))
                .from(includeProductsAndPrice ? Math.toIntExact(criteria.offset()) : 0)
                .size(includeProductsAndPrice ? criteria.size() : 0)
                .trackTotalHits(t -> t.enabled(true))
                .aggregations(BRANDS, a -> a.composite(composite.build())
                        .aggregations(LABEL, label -> label.terms(t -> t.field("brandLabel").size(1)
                                .order(List.of(Map.of("_key", SortOrder.Asc))))));
        if (includeProductsAndPrice) {
            request.aggregations("price", a -> a.stats(s -> s.field("basePrice")));
            switch (criteria.sort()) {
                case RELEVANCE -> request.sort(s -> s.score(score -> score.order(SortOrder.Desc)))
                        .sort(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)));
                case NEWEST -> request.sort(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)));
                case PRICE_ASC -> request.sort(s -> s.field(f -> f.field("basePrice").order(SortOrder.Asc)));
                case PRICE_DESC -> request.sort(s -> s.field(f -> f.field("basePrice").order(SortOrder.Desc)));
            }
            request.sort(s -> s.field(f -> f.field("id").order(SortOrder.Desc)));
        }
        return request.build();
    }

    protected abstract Query buildTextQuery(String keyword);
}
