package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexBuildCommand;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.port.output.ProductSearchIndexMetadataPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AdminProductSearchIndexServiceTest {
    private final ProductSearchIndexMetadataPort metadata = mock(ProductSearchIndexMetadataPort.class);
    private final AdminProductSearchIndexService service = new AdminProductSearchIndexService(metadata);

    @ParameterizedTest
    @MethodSource("invalidRegistrationRequests")
    void register_withInvalidRequest_reportsSpecificRuleBeforePersistence(
            RegisterProductSearchIndexBuildCommand command, String message) {
        assertThatThrownBy(() -> service.register(command))
                .isInstanceOfSatisfying(ProductSearchIndexManagementException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ProductSearchIndexManagementException.Code.INVALID_INDEX_MANAGEMENT_REQUEST);
                    assertThat(exception).hasMessageContaining(message);
                });
        verifyNoInteractions(metadata);
    }

    static Stream<Arguments> invalidRegistrationRequests() {
        return Stream.of(
                Arguments.of(null, "request is required"),
                Arguments.of(new RegisterProductSearchIndexBuildCommand(null, "key", 500, true), "requestedBy"),
                Arguments.of(new RegisterProductSearchIndexBuildCommand(" ", "key", 500, true), "requestedBy"),
                Arguments.of(new RegisterProductSearchIndexBuildCommand("a".repeat(256), "key", 500, true), "requestedBy"),
                Arguments.of(new RegisterProductSearchIndexBuildCommand("admin", null, 500, true), "idempotencyKey"),
                Arguments.of(new RegisterProductSearchIndexBuildCommand("admin", "bad key", 500, true), "idempotencyKey"),
                Arguments.of(new RegisterProductSearchIndexBuildCommand("admin", "a".repeat(129), 500, true), "idempotencyKey"),
                Arguments.of(new RegisterProductSearchIndexBuildCommand("admin", "key", 0, true), "batchSize"),
                Arguments.of(new RegisterProductSearchIndexBuildCommand("admin", "key", 1001, true), "batchSize"),
                Arguments.of(new RegisterProductSearchIndexBuildCommand("admin", "key", 500, false), "Catalog writes must be paused"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1000})
    void register_withValidBoundaryValues_forwardsUnchangedCommand(int batchSize) {
        var command = new RegisterProductSearchIndexBuildCommand("a".repeat(255), "b".repeat(128), batchSize, true);
        var job = ProductSearchIndexBuildJob.queue(UUID.randomUUID(), UUID.randomUUID(), command.requestedBy(),
                command.idempotencyKey(), batchSize, Instant.now());
        when(metadata.register(command)).thenReturn(job);
        assertThat(service.register(command)).isSameAs(job);
        verify(metadata).register(command);
    }

    @Test
    void getJob_whenMissing_reportsNotFound() {
        UUID id = UUID.randomUUID();
        when(metadata.findJob(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getJob(id)).isInstanceOfSatisfying(ProductSearchIndexManagementException.class,
                e -> assertThat(e.code()).isEqualTo(ProductSearchIndexManagementException.Code.INDEX_BUILD_NOT_FOUND));
    }

    @Test
    void listVersions_withInvalidPage_rejectsBeforePersistence() {
        assertThatThrownBy(() -> service.listVersions(-1, 20)).isInstanceOf(ProductSearchIndexManagementException.class);
        assertThatThrownBy(() -> service.listVersions(0, 101)).isInstanceOf(ProductSearchIndexManagementException.class);
        verifyNoInteractions(metadata);
    }
}
