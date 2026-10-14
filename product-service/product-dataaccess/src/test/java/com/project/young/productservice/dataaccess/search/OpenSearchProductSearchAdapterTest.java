package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.application.dto.query.ProductSearchQuery;
import com.project.young.productservice.application.exception.ProductSearchUnavailableException;
import com.project.young.productservice.application.service.ProductSearchQueryValidator;
import com.project.young.productservice.dataaccess.config.OpenSearchProductProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.CreatePitRequest;
import org.opensearch.client.opensearch.core.CreatePitResponse;
import org.opensearch.client.opensearch.core.DeletePitRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch.core.search.Hit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenSearchProductSearchAdapterTest {

    private OpenSearchClient client;
    private OpenSearchProductSearchAdapter adapter;
    private final ProductSearchCategoryNameReader categoryNames = mock(ProductSearchCategoryNameReader.class);

    @BeforeEach
    void setUp() throws Exception {
        client = mock(OpenSearchClient.class);
        when(client.createPit(any(CreatePitRequest.class))).thenReturn(CreatePitResponse.of(p -> p
                .pitId("test-pit").creationTime(1L).shards(s -> s.total(2).successful(2).failed(0))));
        adapter = new OpenSearchProductSearchAdapter(client, new CrossFieldsProductQueryBuilder(),
                new OpenSearchProductProperties(URI.create("http://localhost:9200"), "products-read", 1000, 5000, 3, 10000), categoryNames);
    }

    @Test
    void search_batchLoadsDistinctCategoryNamesAndPreservesHitOrder() throws Exception {
        var first = document(12);
        var second = document(34);
        var third = document(12);
        var response = priceResponse(3);
        when(response.hits().hits()).thenReturn(List.of(
                Hit.of(h -> h.index("products-v1").id(first.id()).source(first)),
                Hit.of(h -> h.index("products-v1").id(second.id()).source(second)),
                Hit.of(h -> h.index("products-v1").id(third.id()).source(third))));
        when(response.aggregations().get("price").stats().min()).thenReturn(10.0);
        when(response.aggregations().get("price").stats().max()).thenReturn(10.0);
        when(client.search(any(SearchRequest.class), eq(OpenSearchProductDocument.class))).thenReturn(response);
        when(categoryNames.read(Set.of(12L, 34L))).thenReturn(Map.of(12L, "현재 이름", 34L, "다른 카테고리"));
        var result = adapter.search(criteria());
        assertThat(result.content()).extracting(p -> p.id().toString()).containsExactly(first.id(), second.id(), third.id());
        assertThat(result.content()).extracting(p -> p.categoryName()).containsExactly("현재 이름", "다른 카테고리", "현재 이름");
        verify(categoryNames).read(Set.of(12L, 34L));
    }

    @Test
    void search_missingCategoryName_failsInsteadOfReturningIncompleteResult() throws Exception {
        var document = document(12);
        var response = priceResponse(1);
        when(response.hits().hits()).thenReturn(List.of(Hit.of(h -> h.index("products-v1").source(document))));
        when(client.search(any(SearchRequest.class), eq(OpenSearchProductDocument.class))).thenReturn(response);
        when(categoryNames.read(Set.of(12L))).thenReturn(Map.of());
        assertThatThrownBy(() -> adapter.search(criteria())).isInstanceOf(ProductSearchUnavailableException.class)
                .hasRootCauseMessage("Category name is missing for a search result");
        verify(client).deletePit(any(DeletePitRequest.class));
    }

    private static OpenSearchProductDocument document(long categoryId) {
        var original = ProductSearchInitialLoaderTest.document();
        return new OpenSearchProductDocument(original.id(), categoryId, original.name(), original.brand(), original.brandKey(),
                original.brandLabel(), original.description(), original.mainImageUrl(), original.basePrice(),
                original.productStatus(), original.categoryStatus(), original.createdAt());
    }

    @Test
    void transportFailureIsUnavailableAndClosesPit() throws Exception {
        when(client.search(any(SearchRequest.class), eq(OpenSearchProductDocument.class))).thenThrow(new IOException("timeout"));
        assertUnavailableAndClosed();
    }

    @Test
    void partialShardFailureIsUnavailableAndClosesPit() throws Exception {
        SearchResponse<OpenSearchProductDocument> response = mockResponse();
        when(response.shards().failed()).thenReturn(1);
        when(client.search(any(SearchRequest.class), eq(OpenSearchProductDocument.class))).thenReturn(response);
        assertUnavailableAndClosed();
    }

    @Test
    void timedOutResponseIsUnavailableAndClosesPit() throws Exception {
        SearchResponse<OpenSearchProductDocument> response = mockResponse();
        when(response.timedOut()).thenReturn(true);
        when(client.search(any(SearchRequest.class), eq(OpenSearchProductDocument.class))).thenReturn(response);
        assertUnavailableAndClosed();
    }

    @Test
    void missingAggregationsAreUnavailableRatherThanAnEmptyResult() throws Exception {
        when(client.search(any(SearchRequest.class), eq(OpenSearchProductDocument.class))).thenReturn(mockResponse());
        assertUnavailableAndClosed();
    }

    @Test
    void missingPriceStatisticsAreExplicitlyRejectedAndClosesPit() throws Exception {
        var response = priceResponse(1);
        when(response.aggregations().get("price")).thenReturn(null);
        when(client.search(any(SearchRequest.class), eq(OpenSearchProductDocument.class))).thenReturn(response);
        assertThatThrownBy(() -> adapter.search(criteria()))
                .isInstanceOf(ProductSearchUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Missing price statistics");
        verify(client).deletePit(any(DeletePitRequest.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void missingPriceBoundIsExplicitlyRejectedAndClosesPit(boolean missingMin) throws Exception {
        var response = priceResponse(1);
        var price = response.aggregations().get("price").stats();
        when(price.min()).thenReturn(missingMin ? null : Double.valueOf(10));
        when(price.max()).thenReturn(missingMin ? Double.valueOf(10) : null);
        when(client.search(any(SearchRequest.class), eq(OpenSearchProductDocument.class))).thenReturn(response);
        assertThatThrownBy(() -> adapter.search(criteria()))
                .isInstanceOf(ProductSearchUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Missing price bounds");
        verify(client).deletePit(any(DeletePitRequest.class));
    }

    @Test
    void emptyResultsAllowNullPriceBoundsAndClosesPit() throws Exception {
        var response = priceResponse(0);
        when(response.aggregations().get("price").stats().min()).thenReturn(null);
        when(response.aggregations().get("price").stats().max()).thenReturn(null);
        when(client.search(any(SearchRequest.class), eq(OpenSearchProductDocument.class))).thenReturn(response);
        var result = adapter.search(criteria());
        assertThat(result.minPrice()).isNull();
        assertThat(result.maxPrice()).isNull();
        org.mockito.Mockito.verifyNoInteractions(categoryNames);
        verify(client).deletePit(any(DeletePitRequest.class));
    }

    @Test
    void failedPitCreationIsUnavailable() throws Exception {
        when(client.createPit(any(CreatePitRequest.class))).thenThrow(new IOException("connection refused"));
        assertThatThrownBy(() -> adapter.search(criteria())).isInstanceOf(ProductSearchUnavailableException.class);
    }

    private static SearchResponse<OpenSearchProductDocument> priceResponse(long count) {
        var response = mockResponse();
        when(response.aggregations().get(OpenSearchProductQueryBuilder.BRANDS).composite().buckets().array())
                .thenReturn(List.of());
        when(response.hits().total().relation()).thenReturn(TotalHitsRelation.Eq);
        when(response.hits().total().value()).thenReturn(count);
        when(response.hits().hits()).thenReturn(List.of());
        when(response.aggregations().get("price").isStats()).thenReturn(true);
        when(response.aggregations().get("price").stats().count()).thenReturn(count);
        return response;
    }

    private void assertUnavailableAndClosed() throws Exception {
        assertThatThrownBy(() -> adapter.search(criteria())).isInstanceOf(ProductSearchUnavailableException.class);
        verify(client).deletePit(any(DeletePitRequest.class));
    }

    private static com.project.young.productservice.application.dto.condition.ProductSearchCriteria criteria() {
        return new ProductSearchQueryValidator().validate(new ProductSearchQuery(null, null, null, null, null, null, null, null));
    }

    @SuppressWarnings("unchecked")
    private static SearchResponse<OpenSearchProductDocument> mockResponse() {
        return mock(SearchResponse.class, RETURNS_DEEP_STUBS);
    }
}
