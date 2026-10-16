package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.application.dto.condition.ProductSearchCriteria;
import com.project.young.productservice.application.dto.result.ProductSearchResult;
import com.project.young.productservice.application.exception.ProductSearchUnavailableException;
import com.project.young.productservice.application.port.output.ProductSearchPort;
import com.project.young.productservice.dataaccess.config.OpenSearchProductProperties;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.CreatePitRequest;
import org.opensearch.client.opensearch.core.DeletePitRequest;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class OpenSearchProductSearchAdapter implements ProductSearchPort {

    private final OpenSearchClient client;
    private final OpenSearchProductQueryBuilder queryBuilder;
    private final OpenSearchProductProperties properties;
    private final ProductSearchCategoryNameReader categoryNames;

    public OpenSearchProductSearchAdapter(OpenSearchClient client, OpenSearchProductQueryBuilder queryBuilder,
                                         OpenSearchProductProperties properties, ProductSearchCategoryNameReader categoryNames) {
        this.client = client;
        this.queryBuilder = queryBuilder;
        this.properties = properties;
        this.categoryNames = categoryNames;
    }

    @Override
    public ProductSearchResult search(ProductSearchCriteria criteria) {
        String pitId = null;
        long deadline = System.nanoTime() + properties.requestBudgetMillis() * 1_000_000L;
        try {
            var pit = client.createPit(CreatePitRequest.of(p -> p.index(properties.index()).keepAlive(t -> t.time("1m"))
                    .allowPartialPitCreation(false)));
            pitId = pit.pitId();
            if (pitId == null || pitId.isBlank() || pit.shards().failed() > 0) {
                throw new IllegalStateException("Incomplete PIT creation");
            }
            SearchResponse<OpenSearchProductDocument> first = null;
            List<ProductSearchResult.Brand> brands = new ArrayList<>();
            Map<String, FieldValue> after = Map.of();
            do {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("Search request budget exceeded");
                }
                var response = client.search(queryBuilder.build(criteria, pitId, properties.queryTimeoutSeconds(),
                        after, first == null), OpenSearchProductDocument.class);
                verifyComplete(response);
                if (first == null) {
                    first = response;
                }
                var aggregate = response.aggregations().get(OpenSearchProductQueryBuilder.BRANDS).composite();
                for (var bucket : aggregate.buckets().array()) {
                    var labels = bucket.aggregations().get(OpenSearchProductQueryBuilder.LABEL).sterms().buckets().array();
                    if (labels.isEmpty()) {
                        throw new IllegalStateException("Missing brand label");
                    }
                    brands.add(new ProductSearchResult.Brand(bucket.key().get("brand").stringValue(),
                            labels.getFirst().key(), bucket.docCount()));
                }
                Map<String, FieldValue> next = aggregate.afterKey();
                if (aggregate.buckets().array().isEmpty() || next.isEmpty()) {
                    break;
                }
                if (next.equals(after)) {
                    throw new IllegalStateException("Composite aggregation cursor did not advance");
                }
                after = next;
            } while (true);
            var total = first.hits().total();
            if (total == null || total.relation() != TotalHitsRelation.Eq) {
                throw new IllegalStateException("Search total is not exact");
            }
            var documents = first.hits().hits().stream().map(hit -> hit.source()).toList();
            documents.forEach(OpenSearchProductSearchAdapter::validateDocument);
            var ids = documents.stream().map(OpenSearchProductDocument::categoryId).collect(Collectors.toSet());
            var names = ids.isEmpty() ? Map.<Long, String>of() : categoryNames.read(ids);
            var content = documents.stream().map(document -> toProduct(document, names)).toList();
            var priceAggregate = first.aggregations().get("price");
            if (priceAggregate == null || !priceAggregate.isStats()) {
                throw new IllegalStateException("Missing price statistics");
            }
            var price = priceAggregate.stats();
            if (price.count() != total.value()) {
                throw new IllegalStateException("Missing indexed prices");
            }
            BigDecimal minPrice = null;
            BigDecimal maxPrice = null;
            if (price.count() > 0) {
                Double min = price.min();
                Double max = price.max();
                if (min == null || max == null) {
                    throw new IllegalStateException("Missing price bounds");
                }
                minPrice = BigDecimal.valueOf(min).setScale(2, RoundingMode.HALF_UP);
                maxPrice = BigDecimal.valueOf(max).setScale(2, RoundingMode.HALF_UP);
            }
            brands.sort(Comparator.comparingLong(ProductSearchResult.Brand::count).reversed()
                    .thenComparing(ProductSearchResult.Brand::value));
            return new ProductSearchResult(content, total.value(), brands.stream().limit(50).toList(), brands.size() > 50,
                    minPrice, maxPrice);
        } catch (Exception exception) {
            log.warn("Product search failed: categoryId={}, page={}, exceptionType={}",
                    criteria.categoryId(), criteria.page(), exception.getClass().getSimpleName());
            throw new ProductSearchUnavailableException("Search engine could not return complete results", exception);
        } finally {
            if (pitId != null && !pitId.isBlank()) {
                String id = pitId;
                try {
                    client.deletePit(DeletePitRequest.of(p -> p.pitId(List.of(id))));
                } catch (Exception exception) {
                    log.warn("Search PIT cleanup failed: exceptionType={}", exception.getClass().getSimpleName());
                }
            }
        }
    }

    private static void verifyComplete(SearchResponse<?> response) {
        if (response.timedOut() || response.shards().failed() > 0 || Boolean.TRUE.equals(response.terminatedEarly())) {
            throw new IllegalStateException("Incomplete search response");
        }
    }

    private static void validateDocument(OpenSearchProductDocument document) {
        if (document == null || !document.documentReady() || document.categoryId() == null || document.categoryId() <= 0
                || document.name() == null || document.basePrice() == null || document.basePrice().signum() < 0) {
            throw new IllegalStateException("Invalid product search document");
        }
    }

    private static ProductSearchResult.Product toProduct(OpenSearchProductDocument document, Map<Long, String> names) {
        String categoryName = names.get(document.categoryId());
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalStateException("Category name is missing for a search result");
        }
        return new ProductSearchResult.Product(UUID.fromString(document.id()), document.categoryId(),
                categoryName, document.name(), document.brand(), document.mainImageUrl(), document.basePrice());
    }
}
