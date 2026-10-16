package com.project.young.productservice.dataaccess.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildWorkPort.Work;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import com.project.young.productservice.dataaccess.config.OpenSearchProductProperties;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexMappingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.CountResponse;
import org.opensearch.client.opensearch.core.CreatePitRequest;
import org.opensearch.client.opensearch.core.CreatePitResponse;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class OpenSearchProductIndexBuildExecutionAdapterTest {
    private final OpenSearchClient client = mock(OpenSearchClient.class, RETURNS_DEEP_STUBS);
    private final ProductSearchIndexVerificationReader reader = mock(ProductSearchIndexVerificationReader.class);
    private final List<CountRequest> counts = new ArrayList<>();
    private final BigDecimal price = new BigDecimal("10.00");
    private final OpenSearchProductDocument sample = new OpenSearchProductDocument(
            UUID.randomUUID().toString(), 12L, "Sample", null, null, null, null, null,
            price, "ACTIVE", "ACTIVE", "2026-10-14T00:00:00Z", 1, true);
    private final Work work = new Work(null, ProductSearchIndexVersion.register(UUID.randomUUID(), 1, 1,
            "0".repeat(64), "admin", Instant.now()), null);
    private OpenSearchProductIndexBuildExecutionAdapter adapter;
    private SearchResponse<OpenSearchProductDocument> response;
    private long totalCount = 2;
    private long sampleMatches = 1;
    private int failedShards;

    @BeforeEach
    void setUp() throws Exception {
        when(client.createPit(any(CreatePitRequest.class))).thenReturn(CreatePitResponse.of(p -> p
                .pitId("test-pit").creationTime(1L).shards(s -> s.total(2).successful(2).failed(0))));
        response = (SearchResponse<OpenSearchProductDocument>) mock(SearchResponse.class, RETURNS_DEEP_STUBS);
        when(response.aggregations().get(OpenSearchProductQueryBuilder.BRANDS).composite().buckets().array())
                .thenReturn(List.of());
        when(response.hits().total().relation()).thenReturn(TotalHitsRelation.Eq);
        when(response.hits().total().value()).thenReturn(2L);
        when(response.hits().hits()).thenReturn(List.of());
        when(response.aggregations().get("price").isStats()).thenReturn(true);
        when(response.aggregations().get("price").stats().count()).thenReturn(2L);
        when(response.aggregations().get("price").stats().min()).thenReturn(10.0);
        when(response.aggregations().get("price").stats().max()).thenReturn(10.0);
        when(client.search(any(SearchRequest.class), eq(OpenSearchProductDocument.class))).thenReturn(response);
        var stored = (GetResponse<OpenSearchProductDocument>) mock(GetResponse.class);
        when(stored.source()).thenReturn(sample);
        when(client.get(any(Function.class), eq(OpenSearchProductDocument.class))).thenReturn(stored);
        when(reader.read()).thenReturn(new ProductSearchIndexVerificationReader.Snapshot(2, price, price, sample));
        when(client.count(any(Function.class))).thenAnswer(invocation -> {
            Function<CountRequest.Builder, ?> build = invocation.getArgument(0);
            var request = ((CountRequest.Builder) build.apply(new CountRequest.Builder())).build();
            counts.add(request);
            boolean targeted = request.query() != null;
            return CountResponse.of(c -> c.count(targeted ? sampleMatches : totalCount)
                    .shards(s -> s.total(2).successful(targeted ? 2 - failedShards : 2)
                            .failed(targeted ? failedShards : 0)));
        });
        adapter = new OpenSearchProductIndexBuildExecutionAdapter(client, new ObjectMapper(),
                mock(ProductSearchInitialLoader.class), reader, new CrossFieldsProductQueryBuilder(),
                new OpenSearchProductProperties(URI.create("http://localhost:9200"), "products-read", 1000, 5000, 3, 10000),
                new ProductSearchIndexMappingProperties(new ClassPathResource("opensearch/products-index.json")), mock(ProductSearchCategoryNameReader.class));
    }

    @Test
    void verify_sampleMatchesFilters_checksItsIdAndReturnsFullCount() {
        assertThat(adapter.verify(work, () -> {})).isEqualTo(2);
        var request = counts.getLast();
        assertThat(request.index()).containsExactly(work.version().indexName());
        var filters = request.query().bool().filter();
        assertThat(filters.get(1).term().field()).isEqualTo("id");
        assertThat(filters.get(1).term().value().stringValue()).isEqualTo(sample.id());
        var publicFilters = filters.getFirst().bool().filter();
        assertThat(publicFilters.get(1).term().field()).isEqualTo("productStatus");
        assertThat(publicFilters.get(2).term().field()).isEqualTo("categoryStatus");
        assertThat(publicFilters.get(3).term().value().longValue()).isEqualTo(sample.categoryId());
        assertThat(publicFilters.get(4).range().gte().to(BigDecimal.class)).isEqualByComparingTo(price);
        assertThat(publicFilters.get(4).range().lte().to(BigDecimal.class)).isEqualByComparingTo(price);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "2,0", "1,1"})
    void verify_otherProductsMatchButSampleMissingOrCountIncomplete_rejects(long matches, int failed) {
        sampleMatches = matches;
        failedShards = failed;
        assertThatThrownBy(() -> adapter.verify(work, () -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Catalog sample does not match category, brand or price filters");
    }

    @Test
    void verify_noVisibleSample_skipsTargetedQuery() {
        totalCount = 0;
        when(response.hits().total().value()).thenReturn(0L);
        when(response.aggregations().get("price").stats().count()).thenReturn(0L);
        when(reader.read()).thenReturn(new ProductSearchIndexVerificationReader.Snapshot(0, null, null, null));
        assertThat(adapter.verify(work, () -> {})).isZero();
        assertThat(counts).hasSize(1);
        assertThat(counts.getFirst().query()).isNull();
    }
}
