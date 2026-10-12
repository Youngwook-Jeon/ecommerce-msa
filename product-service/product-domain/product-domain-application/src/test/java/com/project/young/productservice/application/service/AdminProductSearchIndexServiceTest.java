package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexBuildCommand;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.port.output.ProductSearchIndexMetadataPort;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminProductSearchIndexServiceTest {
    private final ProductSearchIndexMetadataPort metadata = mock(ProductSearchIndexMetadataPort.class);
    private final AdminProductSearchIndexService service = new AdminProductSearchIndexService(metadata);

    @Test
    void register_withInvalidHeaderOrPause_rejectsBeforePersistence() {
        assertThatThrownBy(() -> service.register(new RegisterProductSearchIndexBuildCommand("admin", "bad key", 500, true)))
                .isInstanceOf(ProductSearchIndexManagementException.class);
        assertThatThrownBy(() -> service.register(new RegisterProductSearchIndexBuildCommand("admin", "key", 500, false)))
                .isInstanceOf(ProductSearchIndexManagementException.class);
        verifyNoInteractions(metadata);
    }

    @Test
    void getJob_whenMissing_reportsNotFound() {
        UUID id = UUID.randomUUID();
        when(metadata.findJob(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getJob(id)).isInstanceOfSatisfying(ProductSearchIndexManagementException.class,
                e -> org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo(ProductSearchIndexManagementException.Code.INDEX_BUILD_NOT_FOUND));
    }

    @Test
    void listVersions_withInvalidPage_rejectsBeforePersistence() {
        assertThatThrownBy(() -> service.listVersions(-1, 20)).isInstanceOf(ProductSearchIndexManagementException.class);
        assertThatThrownBy(() -> service.listVersions(0, 101)).isInstanceOf(ProductSearchIndexManagementException.class);
        verifyNoInteractions(metadata);
    }
}
