package com.project.young.productservice.dataaccess.search;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductSearchInitialLoaderTest {

    private final ProductSearchIndexBatchReader reader = mock(ProductSearchIndexBatchReader.class);
    private final OpenSearchProductBulkWriter writer = mock(OpenSearchProductBulkWriter.class);
    private final ProductSearchInitialLoader loader = new ProductSearchInitialLoader(reader, writer);

    @Test
    void readsMapsAndWritesEachBatchBeforeAdvancingCursor() {
        var first = document();
        var second = document();
        when(reader.read(null, 1)).thenReturn(List.of(first));
        when(reader.read(UUID.fromString(first.id()), 1)).thenReturn(List.of(second));
        when(reader.read(UUID.fromString(second.id()), 1)).thenReturn(List.of());
        assertThat(loader.load("products-v1", 1)).isEqualTo(new ProductSearchInitialLoader.Result(2, 2));
        InOrder order = inOrder(reader, writer);
        order.verify(writer).verifyTarget("products-v1");
        order.verify(reader).read(null, 1);
        order.verify(writer).write(org.mockito.ArgumentMatchers.eq("products-v1"), org.mockito.ArgumentMatchers.eq(List.of(first)), any(Runnable.class));
        order.verify(reader).read(UUID.fromString(first.id()), 1);
        order.verify(writer).write(org.mockito.ArgumentMatchers.eq("products-v1"), org.mockito.ArgumentMatchers.eq(List.of(second)), any(Runnable.class));
        order.verify(reader).read(UUID.fromString(second.id()), 1);
    }

    @Test
    void emptyCatalogDoesNotSendBulk() {
        when(reader.read(null, 500)).thenReturn(List.of());
        assertThat(loader.load("products-v1", 500)).isEqualTo(new ProductSearchInitialLoader.Result(0, 0));
        verify(writer, never()).write(anyString(), anyList(), any(Runnable.class));
    }

    @Test
    void failedBatchStopsBeforeReadingAnotherBatch() {
        var document = document();
        when(reader.read(null, 1)).thenReturn(List.of(document));
        doThrow(new ProductSearchIndexingException("failed")).when(writer).write(anyString(), anyList(), any(Runnable.class));
        assertThatThrownBy(() -> loader.load("products-v1", 1)).isInstanceOf(ProductSearchIndexingException.class);
        verify(reader, times(1)).read(any(), anyInt());
    }

    @Test
    void badDocumentStopsBeforeBulk() {
        when(reader.read(null, 1)).thenThrow(new IllegalArgumentException("bad price"));
        assertThatThrownBy(() -> loader.load("products-v1", 1)).isInstanceOf(IllegalArgumentException.class);
        verify(writer, never()).write(anyString(), anyList(), any(Runnable.class));
    }

    @Test
    void validatesBatchSizeBeforeAnyIo() {
        assertThatThrownBy(() -> loader.load("products-v1", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loader.load("products-v1", 1001)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reader, writer);
    }

    static OpenSearchProductDocument document() {
        return new OpenSearchProductDocument(UUID.randomUUID().toString(), 12, "이어폰", "이어폰", "Example",
                "example", "Example", "방수", null, new BigDecimal("10.00"), "ACTIVE", "ACTIVE", "2026-10-11T00:00:00Z");
    }
}
