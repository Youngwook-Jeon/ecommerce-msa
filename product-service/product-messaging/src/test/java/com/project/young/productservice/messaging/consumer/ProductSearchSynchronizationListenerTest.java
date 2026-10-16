package com.project.young.productservice.messaging.consumer;

import com.project.young.productservice.application.exception.ProductSearchSynchronizationDeferredException;
import com.project.young.productservice.application.port.output.ProductSearchSynchronizationPort;
import com.project.young.productservice.messaging.dto.ProductCatalogChangedMessage;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

class ProductSearchSynchronizationListenerTest {
    private final ProductSearchSynchronizationPort port = mock(ProductSearchSynchronizationPort.class);
    private final ProductSearchSynchronizationListener listener = new ProductSearchSynchronizationListener(port);

    @Test
    void change_andDltReplay_bothRequeryUsingProductAndRevision() {
        var message = message();
        listener.onChange(message);
        listener.replay(message);
        verify(port, org.mockito.Mockito.times(2)).synchronize(message.productId(), 7L);
    }

    @Test
    void deferredIndex_isPropagatedWithoutAcknowledging() {
        var message = message();
        doThrow(new ProductSearchSynchronizationDeferredException("activation unresolved")).when(port).synchronize(message.productId(), 7L);
        assertThatThrownBy(() -> listener.onChange(message)).isInstanceOf(ProductSearchSynchronizationDeferredException.class);
    }

    @Test
    void malformedMessage_isNotSilentlySkipped() {
        assertThatThrownBy(() -> listener.onChange(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void catalogRevision_readsAndWritesCdcField() throws Exception {
        var mapper = new ObjectMapper();
        var newMessage = mapper.readValue("{\"catalog_revision\":8}", ProductCatalogChangedMessage.class);
        assertThat(newMessage.catalogRevision()).isEqualTo(8L);
        assertThat(mapper.writeValueAsString(newMessage)).contains("\"catalog_revision\":8");
    }

    private static ProductCatalogChangedMessage message() {
        return new ProductCatalogChangedMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "PRODUCT_UPDATED", null, null, null, 7L);
    }
}
