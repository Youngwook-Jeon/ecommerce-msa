package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexActivationCommand;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationMetadataPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminProductSearchIndexActivationServiceTest {
    private final ProductSearchIndexActivationMetadataPort metadata = mock(ProductSearchIndexActivationMetadataPort.class);
    private final ProductSearchIndexAliasPort aliases = mock(ProductSearchIndexAliasPort.class);
    private final AdminProductSearchIndexActivationService service = new AdminProductSearchIndexActivationService(metadata, aliases);
    private final Instant now = Instant.now();
    private final ProductSearchIndexVersion target = ProductSearchIndexVersion.register(UUID.randomUUID(), 1, 1, "a".repeat(64), "admin", now)
            .beginBuild(now).markReady(now);
    private final RegisterProductSearchIndexActivationCommand command = new RegisterProductSearchIndexActivationCommand(target.id(), null, "admin", "activate-1", true);

    @BeforeEach
    void prepare() {
        when(metadata.findRequest("admin", "activate-1")).thenReturn(Optional.empty());
        when(metadata.context(target.id())).thenReturn(new ProductSearchIndexActivationMetadataPort.Context(target, null, "products-read"));
        when(aliases.readAlias("products-read")).thenReturn(new ProductSearchIndexAliasPort.AliasState(Set.of(), true));
    }

    @Test
    void register_initialActivation_checksAliasThenPersistsIntent() {
        var job = ProductSearchIndexActivationJob.queue(UUID.randomUUID(), target.id(), null, "products-read", "admin", "activate-1", now);
        when(metadata.register(command)).thenReturn(job);
        assertThat(service.register(command)).isEqualTo(job);
        verify(aliases).readAlias("products-read");
        verify(metadata).register(command);
    }

    @Test
    void register_replay_doesNotNeedAliasOrReadyState() {
        var job = ProductSearchIndexActivationJob.queue(UUID.randomUUID(), target.id(), null, "products-read", "admin", "activate-1", now);
        when(metadata.findRequest("admin", "activate-1")).thenReturn(Optional.of(job));
        when(metadata.register(command)).thenReturn(job);
        assertThat(service.register(command)).isEqualTo(job);
        verifyNoInteractions(aliases);
        verify(metadata, never()).context(any());
    }

    @Test
    void register_staleExpectedVersion_rejectsBeforeAliasLookup() {
        var active = ProductSearchIndexVersion.register(UUID.randomUUID(), 2, 1, "a".repeat(64), "admin", now)
                .beginBuild(now).markReady(now).beginActivation(now).confirmActive(now);
        when(metadata.context(target.id())).thenReturn(new ProductSearchIndexActivationMetadataPort.Context(target, active, "products-read"));
        assertCode(() -> service.register(command), "ACTIVE_INDEX_CHANGED");
        verifyNoInteractions(aliases);
        verify(metadata, never()).register(any());
    }

    @Test
    void register_aliasDiffersFromDatabase_rejectsUnknownState() {
        when(aliases.readAlias("products-read")).thenReturn(new ProductSearchIndexAliasPort.AliasState(Set.of("unknown-index"), true));
        assertCode(() -> service.register(command), "INDEX_STATE_UNCONFIRMED");
        verify(metadata, never()).register(any());
    }

    @Test
    void register_filteredAlias_rejectsUnknownState() {
        when(aliases.readAlias("products-read")).thenReturn(new ProductSearchIndexAliasPort.AliasState(Set.of(), false));
        assertCode(() -> service.register(command), "INDEX_STATE_UNCONFIRMED");
    }

    @Test
    void register_withoutWritePauseOrValidKey_isRejected() {
        assertCode(() -> service.register(new RegisterProductSearchIndexActivationCommand(target.id(), null, "admin", "key", false)), "INVALID_INDEX_MANAGEMENT_REQUEST");
        assertCode(() -> service.register(new RegisterProductSearchIndexActivationCommand(target.id(), null, "admin", "invalid key", true)), "INVALID_INDEX_MANAGEMENT_REQUEST");
        assertCode(() -> service.register(null), "INVALID_INDEX_MANAGEMENT_REQUEST");
    }

    @Test
    void lookup_missingActivation_hasSpecificNotFoundCode() {
        UUID id = UUID.randomUUID();
        when(metadata.findJob(id)).thenReturn(Optional.empty());
        assertCode(() -> service.getJob(id), "INDEX_ACTIVATION_NOT_FOUND");
    }

    private static void assertCode(Runnable operation, String code) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ProductSearchIndexManagementException.class,
                exception -> assertThat(exception.code().name()).isEqualTo(code));
    }
}
