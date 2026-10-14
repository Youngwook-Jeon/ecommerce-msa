package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexMappingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.UpdateAliasesRequest;
import org.opensearch.client.opensearch.indices.UpdateAliasesResponse;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenSearchProductIndexAliasAdapterTest {
    private final OpenSearchClient client = mock(OpenSearchClient.class, RETURNS_DEEP_STUBS);
    private final OpenSearchProductIndexAliasAdapter adapter = new OpenSearchProductIndexAliasAdapter(client,
            new ProductSearchIndexMappingProperties(new ClassPathResource("opensearch/products-index.json")));

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void switchAlias_submitsOneRequestWithExactNamesAndRequiredRemoval(boolean hasPrevious) throws Exception {
        when(client.indices().updateAliases(any(UpdateAliasesRequest.class))).thenReturn(UpdateAliasesResponse.of(r -> r.acknowledged(true)));
        var work = work(hasPrevious);
        adapter.switchAlias(work);
        var capture = ArgumentCaptor.forClass(UpdateAliasesRequest.class);
        verify(client.indices()).updateAliases(capture.capture());
        var actions = capture.getValue().actions();
        assertThat(actions).hasSize(hasPrevious ? 2 : 1);
        if (hasPrevious) {
            assertThat(actions.getFirst().remove().index()).isEqualTo("products-v1");
            assertThat(actions.getFirst().remove().alias()).isEqualTo("products-read");
            assertThat(actions.getFirst().remove().mustExist()).isTrue();
        }
        assertThat(actions.getLast().add().index()).isEqualTo("products-v2");
        assertThat(actions.getLast().add().alias()).isEqualTo("products-read");
        assertThat(actions.getLast().add().isWriteIndex()).isFalse();
    }

    @Test
    void switchAlias_transportFailure_doesNotRetry() throws Exception {
        when(client.indices().updateAliases(any(UpdateAliasesRequest.class))).thenThrow(new IOException("response lost"));
        assertThatThrownBy(() -> adapter.switchAlias(work(true))).isInstanceOf(ProductSearchIndexingException.class)
                .hasRootCauseMessage("response lost");
        verify(client.indices()).updateAliases(any(UpdateAliasesRequest.class));
    }

    @Test
    void switchAlias_unacknowledgedResponse_isNotSuccess() throws Exception {
        when(client.indices().updateAliases(any(UpdateAliasesRequest.class))).thenReturn(UpdateAliasesResponse.of(r -> r.acknowledged(false)));
        assertThatThrownBy(() -> adapter.switchAlias(work(false))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Alias switch was not acknowledged");
    }

    private static ProductSearchIndexActivationWorkPort.Work work(boolean hasPrevious) {
        Instant now = Instant.now();
        var version = ProductSearchIndexVersion.register(UUID.randomUUID(), 2, 1, "a".repeat(64), "admin", now)
                .beginBuild(now).markReady(now).beginActivation(now);
        var job = ProductSearchIndexActivationJob.queue(UUID.randomUUID(), version.id(), hasPrevious ? UUID.randomUUID() : null,
                "products-read", "admin", "activate-1", now).start(now);
        return new ProductSearchIndexActivationWorkPort.Work(job, version, hasPrevious ? "products-v1" : null, UUID.randomUUID());
    }
}
