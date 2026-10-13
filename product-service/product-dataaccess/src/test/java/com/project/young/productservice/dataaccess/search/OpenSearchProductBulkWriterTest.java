package com.project.young.productservice.dataaccess.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.OperationType;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.project.young.productservice.application.exception.ProductSearchIndexOwnershipLostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenSearchProductBulkWriterTest {

    private final OpenSearchClient client = mock(OpenSearchClient.class);
    private final OpenSearchProductBulkWriter writer = new OpenSearchProductBulkWriter(client);

    @Test
    void retriesOnlyFailedItemsWithStableIds() throws Exception {
        var a = ProductSearchInitialLoaderTest.document();
        var b = ProductSearchInitialLoaderTest.document();
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(true, item(a, 201), item(b, 429)),
                response(false, item(b, 201)));
        writer.write("products-v1", List.of(a, b));
        var captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(2)).bulk(captor.capture());
        assertThat(captor.getAllValues().get(0).operations()).hasSize(2);
        assertThat(captor.getAllValues().get(1).operations()).hasSize(1);
        assertThat(captor.getAllValues().get(1).operations().getFirst().index().id()).isEqualTo(b.id());
    }

    @Test
    void ambiguousTransportFailureRetriesSameDocuments() throws Exception {
        var a = ProductSearchInitialLoaderTest.document();
        when(client.bulk(any(BulkRequest.class))).thenThrow(new IOException("connection lost"))
                .thenReturn(response(false, item(a, 200)));
        writer.write("products-v1", List.of(a));
        verify(client, times(2)).bulk(any(BulkRequest.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409})
    void permanentFailureDoesNotRetry(int status) throws Exception {
        var a = ProductSearchInitialLoaderTest.document();
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(true, item(a, status)));
        assertThatThrownBy(() -> writer.write("products-v1", List.of(a))).isInstanceOf(ProductSearchIndexingException.class);
        verify(client).bulk(any(BulkRequest.class));
    }

    @Test
    void retryableItemsHaveBoundedAttempts() throws Exception {
        var a = ProductSearchInitialLoaderTest.document();
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(true, item(a, 503)));
        assertThatThrownBy(() -> writer.write("products-v1", List.of(a))).hasMessage("Bulk item retries exhausted");
        verify(client, times(3)).bulk(any(BulkRequest.class));
    }

    @Test
    void missingResponseItemFailsRatherThanCountingSuccess() throws Exception {
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(false));
        assertThatThrownBy(() -> writer.write("products-v1", List.of(ProductSearchInitialLoaderTest.document())))
                .hasMessage("Incomplete bulk response");
    }

    @Test
    void emptyBatchDoesNotCallOpenSearch() {
        writer.write("products-v1", List.of());
        verifyNoInteractions(client);
    }

    @Test
    void bulk_afterOwnershipLoss_doesNotSendRequest() {
        var document = ProductSearchInitialLoaderTest.document();
        assertThatThrownBy(() -> writer.write("products-v1", List.of(document), () -> {
            throw new ProductSearchIndexOwnershipLostException();
        })).isInstanceOf(ProductSearchIndexOwnershipLostException.class);
        verifyNoInteractions(client);
    }

    @Test
    void retry_afterOwnershipLoss_doesNotResendFailedDocument() throws Exception {
        var document = ProductSearchInitialLoaderTest.document();
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(true, item(document, 429)));
        var attempts = new AtomicInteger();
        assertThatThrownBy(() -> writer.write("products-v1", List.of(document), () -> {
            if (attempts.incrementAndGet() > 1) {
                throw new ProductSearchIndexOwnershipLostException();
            }
        })).isInstanceOf(ProductSearchIndexOwnershipLostException.class);
        verify(client).bulk(any(BulkRequest.class));
    }

    private static BulkResponseItem item(OpenSearchProductDocument document, int status) {
        return BulkResponseItem.of(i -> i.operationType(OperationType.Index).id(document.id()).index("products-v1").status(status));
    }

    private static BulkResponse response(boolean errors, BulkResponseItem... items) {
        return BulkResponse.of(b -> b.took(1).errors(errors).items(List.of(items)));
    }
}
