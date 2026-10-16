package com.project.young.productservice.dataaccess.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.query.ProductSearchQuery;
import com.project.young.productservice.application.exception.ProductSearchUnavailableException;
import com.project.young.productservice.application.service.ProductSearchQueryValidator;
import com.project.young.productservice.dataaccess.config.OpenSearchProductProperties;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.mapper.OpenSearchProductDocumentMapper;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.HashMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in against a local Nori-enabled cluster. Only UUID-named test indexes are created/deleted. */
@EnabledIfEnvironmentVariable(named = "OPENSEARCH_TEST_ENDPOINT", matches = ".+")
class OpenSearchProductSearchIntegrationTest {

    private URI endpoint;
    private String index;
    private OpenSearchTransport transport;
    private OpenSearchClient client;
    private OpenSearchProductSearchAdapter adapter;

    private final ProductSearchCategoryNameReader categoryNames = mock(ProductSearchCategoryNameReader.class);
    private final Map<Long, String> names = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
//        endpoint = URI.create("http://localhost:9200");
        when(categoryNames.read(any())).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> names.getOrDefault(id, "이어폰")));
        });
        endpoint = URI.create(System.getenv("OPENSEARCH_TEST_ENDPOINT"));
        index = "product-search-test-" + UUID.randomUUID();
        transport = ApacheHttpClient5TransportBuilder.builder(HttpHost.create(endpoint))
                .setMapper(new JacksonJsonpMapper(new ObjectMapper())).build();
        client = new OpenSearchClient(transport);
        adapter = new OpenSearchProductSearchAdapter(client, new CrossFieldsProductQueryBuilder(),
                new OpenSearchProductProperties(endpoint, index, 1000, 5000, 3, 10000), categoryNames);
        try (var resource = getClass().getResourceAsStream("/opensearch/products-index.json")) {
            assertThat(resource).isNotNull();
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(endpoint.resolve("/" + index))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(new String(resource.readAllBytes(), StandardCharsets.UTF_8)))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (client != null && index != null) {
                client.indices().delete(d -> d.index(index));
            }
        } finally {
            if (transport != null) {
                transport.close();
            }
        }
    }

    @Test
    void bulkWriterOverwritesStableIdsAndRejectsAliasTargets() throws Exception {
        var writer = new OpenSearchProductBulkWriter(client);
        writer.verifyTarget(index);
        var original = doc("이어폰", "Example", "example", "", "10.00", 12, "ACTIVE", "ACTIVE");
        writer.write(index, List.of(original));
        writer.write(index, List.of(original));
        client.indices().refresh(r -> r.index(index));
        assertThat(search(null, null, List.of(), null, null, null, 0, 24).totalElements()).isEqualTo(1);
        String alias = index + "-read";
        client.indices().putAlias(r -> r.index(index).name(alias));
        assertThatThrownBy(() -> writer.verifyTarget(alias)).isInstanceOf(ProductSearchIndexingException.class)
                .hasMessageContaining("physical index");
        assertThatThrownBy(() -> writer.verifyTarget(index + "-missing"))
                .isInstanceOf(ProductSearchIndexingException.class);
    }

    @Test
    void searchesMappedEntityWithNormalizedBrandAndCatalogFields() throws Exception {
        var entity = ProductEntity.builder().id(UUID.randomUUID())
                .category(CategoryEntity.builder().id(12L).name("최신 카테고리").status(CategoryStatusEntity.ACTIVE).build())
                .name("이어폰").description("방수 지원").brand("\u00a0가 Example\u2003")
                .basePrice(new BigDecimal("1.2000")).status(ProductStatusEntity.ACTIVE)
                .createdAt(Instant.parse("2026-10-11T00:00:00.123Z")).build();
        index(List.of(new OpenSearchProductDocumentMapper().toDocument(entity, 1)));
        names.put(12L, "최신 카테고리");
        var result = search("방수", 12L, List.of(" 가 EXAMPLE "), "1.20", "1.20", "newest", 0, 24);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().getFirst().id()).isEqualTo(entity.getId());
        assertThat(result.content().getFirst().categoryName()).isEqualTo("최신 카테고리");
        names.put(12L, "이름 수정됨");
        assertThat(search(null, null, List.of(), null, null, null, 0, 24).content().getFirst().categoryName()).isEqualTo("이름 수정됨");
        var stored = client.get(g -> g.index(index).id(entity.getId().toString()), com.fasterxml.jackson.databind.JsonNode.class).source();
        assertThat(stored.has("categoryName")).isFalse();
        assertThat(result.content().getFirst().brand()).isEqualTo("가 Example");
        assertThat(result.brands().getFirst().value()).isEqualTo("가 example");
        assertThat(result.brands().getFirst().label()).isEqualTo("가 Example");
        assertThat(result.minPrice()).isEqualByComparingTo("1.20");
        assertThat(result.maxPrice()).isEqualByComparingTo("1.20");
    }

    @Test
    void searchesDescriptionAndMultipleFieldsAndExcludesInactiveCatalogEntries() throws Exception {
        var visible = doc("이어폰", "Example", "example", "방수 지원", "99.99", 12, "ACTIVE", "ACTIVE");
        index(List.of(visible,
                doc("방수 이어폰", "Other", "other", "", "49.99", 12, "INACTIVE", "ACTIVE"),
                doc("방수 이어폰", "Other", "other", "", "49.99", 12, "ACTIVE", "INACTIVE")));
        var result = search("Example 방수", null, List.of(), null, null, null, 0, 24);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().getFirst().id().toString()).isEqualTo(visible.id());
        assertThat(result.content().getFirst().categoryName()).isEqualTo("이어폰");
        assertThat(result.brands().getFirst().count()).isEqualTo(1);
        assertThat(result.minPrice()).isEqualByComparingTo("99.99");
    }

    @Test
    void combinesBrandOrCategoryAndInclusivePriceFiltersAndKeepsFacetsIndependentOfPage() throws Exception {
        index(List.of(doc("이어폰", "Apple", "apple", "", "50.00", 12, "ACTIVE", "ACTIVE"),
                doc("이어폰", "Samsung", "samsung", "", "100.00", 12, "ACTIVE", "ACTIVE"),
                doc("이어폰", "Other", "other", "", "75.00", 12, "ACTIVE", "ACTIVE"),
                doc("이어폰", "Apple", "apple", "", "75.00", 13, "ACTIVE", "ACTIVE")));
        var first = search(null, 12L, List.of("apple", "samsung"), "50.00", "100.00", "price_asc", 0, 1);
        var second = search(null, 12L, List.of("apple", "samsung"), "50.00", "100.00", "price_asc", 1, 1);
        assertThat(first.content().getFirst().basePrice()).isEqualByComparingTo("50.00");
        assertThat(second.content().getFirst().basePrice()).isEqualByComparingTo("100.00");
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.brands()).isEqualTo(second.brands());
        assertThat(first.minPrice()).isEqualByComparingTo("50.00");
        assertThat(first.maxPrice()).isEqualByComparingTo("100.00");
        assertThat(search(null, 12L, List.of("apple", "samsung"), "50.00", "100.00", "price_asc", 2, 1)
                .content()).isEmpty();
    }

    @Test
    void normalizesBrandFiltersAndCombinesCaseVariantsUnderOneKey() throws Exception {
        index(List.of(doc("이어폰", "Example", "example", "", "10.00", 12, "ACTIVE", "ACTIVE"),
                doc("이어폰", "EXAMPLE", "example", "", "20.00", 12, "ACTIVE", "ACTIVE")));
        var filtered = search(null, null, List.of(" Example ", "EXAMPLE"), null, null, null, 0, 24);
        assertThat(filtered.totalElements()).isEqualTo(2);
        assertThat(filtered.brands()).hasSize(1);
        assertThat(filtered.brands().getFirst().value()).isEqualTo("example");
        assertThat(filtered.brands().getFirst().label()).isEqualTo("EXAMPLE");
        assertThat(filtered.brands().getFirst().count()).isEqualTo(2);
    }

    @Test
    void returnsExactTopBrandsAcrossCompositePagesAndShardsWithDeterministicLabels() throws Exception {
        List<OpenSearchProductDocument> documents = new ArrayList<>();
        for (int i = 0; i < 110; i++) {
            String brand = String.format(java.util.Locale.ROOT, "brand%03d", i);
            documents.add(doc("이어폰", brand, brand, "", "1.00", 12, "ACTIVE", "ACTIVE"));
        }
        documents.add(doc("이어폰", "ZZZ", "zzz", "", "2.00", 12, "ACTIVE", "ACTIVE"));
        documents.add(doc("이어폰", "zzz", "zzz", "", "2.00", 12, "ACTIVE", "ACTIVE"));
        index(documents);
        var result = search(null, null, List.of(), null, null, "newest", 0, 24);
        assertThat(result.totalElements()).isEqualTo(112);
        assertThat(result.brands()).hasSize(50);
        assertThat(result.hasMoreBrands()).isTrue();
        assertThat(result.brands().getFirst().value()).isEqualTo("zzz");
        assertThat(result.brands().getFirst().label()).isEqualTo("ZZZ");
        assertThat(result.brands().getFirst().count()).isEqualTo(2);
        assertThat(result.brands().get(1).value()).isEqualTo("brand000");
    }

    @Test
    void missingBrandDoesNotCreateBucketAndEmptyResultsHaveNullPrices() throws Exception {
        index(List.of(doc("이어폰", null, null, "", "10.00", 12, "ACTIVE", "ACTIVE")));
        var all = search(null, null, List.of(), null, null, null, 0, 24);
        assertThat(all.brands()).isEmpty();
        assertThat(all.totalElements()).isEqualTo(1);
        var empty = search("없는검색어", null, List.of(), null, null, null, 0, 24);
        assertThat(empty.content()).isEmpty();
        assertThat(empty.totalElements()).isZero();
        assertThat(empty.minPrice()).isNull();
        assertThat(empty.maxPrice()).isNull();
    }

    @Test
    void ranksNameMatchesAboveDescriptionMatchesAndSupportsExplicitSorts() throws Exception {
        var nameMatch = doc("방수 이어폰", "Example", "example", "", "100.00", 12, "ACTIVE", "ACTIVE");
        var descriptionMatch = doc("음향 기기", "Example", "example", "방수 이어폰", "50.00", 12, "ACTIVE", "ACTIVE");
        nameMatch = new OpenSearchProductDocument(nameMatch.id(), nameMatch.categoryId(),
                nameMatch.name(), nameMatch.brand(), nameMatch.brandKey(), nameMatch.brandLabel(), nameMatch.description(),
                nameMatch.mainImageUrl(), nameMatch.basePrice(), nameMatch.productStatus(), nameMatch.categoryStatus(),
                "2026-10-09T00:00:00Z", 1, true);
        index(List.of(nameMatch, descriptionMatch));
        assertThat(search("방수 이어폰", null, List.of(), null, null, null, 0, 24)
                .content().getFirst().id().toString()).isEqualTo(nameMatch.id());
        assertThat(search(null, null, List.of(), null, null, "newest", 0, 24)
                .content().getFirst().id().toString()).isEqualTo(descriptionMatch.id());
        assertThat(search(null, null, List.of(), null, null, "price_asc", 0, 24)
                .content().getFirst().id().toString()).isEqualTo(descriptionMatch.id());
        assertThat(search(null, null, List.of(), null, null, "price_desc", 0, 24)
                .content().getFirst().id().toString()).isEqualTo(nameMatch.id());
    }

    @Test
    void rejectsMalformedIndexedProductRatherThanReturningPartialResults() throws Exception {
        index(List.of(doc("이어폰", "Example", "example", "", "-1.00", 12, "ACTIVE", "ACTIVE")));
        assertThatThrownBy(() -> search(null, null, List.of(), null, null, null, 0, 24))
                .isInstanceOf(ProductSearchUnavailableException.class);
    }

    @Test
    void missingIndexIsUnavailableRatherThanAnEmptyResult() {
        var missing = new OpenSearchProductSearchAdapter(client, new CrossFieldsProductQueryBuilder(),
                new OpenSearchProductProperties(endpoint, index + "-missing", 1000, 5000, 3, 10000), categoryNames);
        var criteria = new ProductSearchQueryValidator().validate(
                new ProductSearchQuery(null, null, List.of(), null, null, null, 0, 24));
        assertThatThrownBy(() -> missing.search(criteria)).isInstanceOf(ProductSearchUnavailableException.class);
    }

    private void index(List<OpenSearchProductDocument> documents) throws Exception {
        var bulk = new BulkRequest.Builder().refresh(Refresh.True);
        documents.forEach(document -> bulk.operations(o -> o.index(i -> i.index(index).id(document.id()).document(document))));
        var response = client.bulk(bulk.build());
        assertThat(response.errors()).isFalse();
    }

    private com.project.young.productservice.application.dto.result.ProductSearchResult search(
            String q, Long category, List<String> brands, String min, String max, String sort, int page, int size) {
        return adapter.search(new ProductSearchQueryValidator().validate(new ProductSearchQuery(q, category, brands,
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), sort, page, size)));
    }

    private static OpenSearchProductDocument doc(String name, String brand, String key, String description,
                                                  String price, long categoryId, String status, String categoryStatus) {
        return new OpenSearchProductDocument(UUID.randomUUID().toString(), categoryId, name, brand, key, brand,
                description, null, new BigDecimal(price), status, categoryStatus, "2026-10-10T00:00:00Z", 1, true);
    }
}
