package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.application.exception.ProductSearchSynchronizationDeferredException;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasState;
import com.project.young.productservice.dataaccess.config.OpenSearchProductProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class OpenSearchProductSynchronizationAdapterTest {
    private final ProductSearchSynchronizationReader documents = mock(ProductSearchSynchronizationReader.class);
    private final ProductSearchSynchronizationTargetReader targets = mock(ProductSearchSynchronizationTargetReader.class);
    private final OpenSearchProductBulkWriter writer = mock(OpenSearchProductBulkWriter.class);
    private final ProductSearchIndexAliasPort aliases = mock(ProductSearchIndexAliasPort.class);
    private final ProductSearchSynchronizationTargetReader.Target target = new ProductSearchSynchronizationTargetReader.Target(UUID.randomUUID(), "products-v1");
    private final OpenSearchProductDocument document = ProductSearchInitialLoaderTest.document();
    private final UUID id = UUID.fromString(document.id());
    private final OpenSearchProductSynchronizationAdapter adapter = new OpenSearchProductSynchronizationAdapter(documents, targets, writer, aliases,
            new OpenSearchProductProperties(URI.create("http://localhost:9200"), "products-read", 1000, 5000, 3, 10000));

    @BeforeEach
    void prepare() {
        org.mockito.Mockito.lenient().when(targets.read()).thenReturn(target);
        org.mockito.Mockito.lenient().when(documents.read(id, 1L)).thenReturn(document);
        org.mockito.Mockito.lenient().when(aliases.readAlias("products-read")).thenReturn(new ProductSearchIndexAliasState(Set.of("products-v1"), true));
    }

    @Test
    void validTarget_writesPhysicalIndexAndChecksMetadataAgain() {
        adapter.synchronize(id, 1L);
        verify(writer).verifyTarget("products-v1");
        verify(writer).write(eq("products-v1"), eq(java.util.List.of(document)), any(Runnable.class));
    }

    @Test
    void noActiveIndex_defersBeforeReadingOrWritingDocument() {
        when(targets.read()).thenThrow(new ProductSearchSynchronizationDeferredException("no active index"));
        assertThatThrownBy(() -> adapter.synchronize(id, 1L)).isInstanceOf(ProductSearchSynchronizationDeferredException.class);
        verifyNoInteractions(documents, writer, aliases);
    }

    @Test
    void aliasMismatch_defersWithoutWritingToWrongIndex() {
        when(aliases.readAlias("products-read")).thenReturn(new ProductSearchIndexAliasState(Set.of("products-v2"), true));
        assertThatThrownBy(() -> adapter.synchronize(id, 1L)).isInstanceOf(ProductSearchSynchronizationDeferredException.class);
        verifyNoInteractions(writer);
    }

    @Test
    void targetChangedAfterWrite_keepsKafkaRecordPending() {
        when(targets.read()).thenReturn(target, new ProductSearchSynchronizationTargetReader.Target(UUID.randomUUID(), "products-v2"));
        assertThatThrownBy(() -> adapter.synchronize(id, 1L)).isInstanceOf(ProductSearchSynchronizationDeferredException.class);
        verify(writer).write(eq("products-v1"), any(), any(Runnable.class));
    }

    @Test
    void bulkFailure_isPropagatedForRetry() {
        doThrow(new ProductSearchIndexingException("bulk failed")).when(writer).write(any(), any(), any(Runnable.class));
        assertThatThrownBy(() -> adapter.synchronize(id, 1L)).hasMessage("bulk failed");
    }
}
