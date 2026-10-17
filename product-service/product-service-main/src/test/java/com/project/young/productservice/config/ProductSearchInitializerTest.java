package com.project.young.productservice.config;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexActivationCommand;
import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexBuildCommand;
import com.project.young.productservice.application.port.output.ProductSearchIndexMetadataPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import com.project.young.productservice.application.service.AdminProductSearchIndexActivationService;
import com.project.young.productservice.application.service.AdminProductSearchIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductSearchInitializerTest {
    private final ProductSearchIndexMetadataPort metadata = mock(ProductSearchIndexMetadataPort.class);
    private final AdminProductSearchIndexService builds = mock(AdminProductSearchIndexService.class);
    private final AdminProductSearchIndexActivationService activations = mock(AdminProductSearchIndexActivationService.class);
    private final Instant now = Instant.parse("2026-10-16T00:00:00Z");
    private final UUID versionId = UUID.randomUUID();
    private final ProductSearchIndexBuildJob build = ProductSearchIndexBuildJob.queue(UUID.randomUUID(), versionId,
            "system-initial-load", "initial-load", 500, now);
    private final ProductSearchIndexActivationJob activation = ProductSearchIndexActivationJob.queue(UUID.randomUUID(),
            versionId, null, "products-read", "system-initial-load", "initial-load-" + versionId, now);

    @Test
    void existingActiveIndex_skipsInitialLoad() {
        var active = ProductSearchIndexVersion.register(versionId, 1, 1, "a".repeat(64), "admin", now)
                .beginBuild(now).markReady(now).beginActivation(now).confirmActive(now);
        when(metadata.findActiveVersion()).thenReturn(Optional.of(active));
        initializer(1000).run(new DefaultApplicationArguments());
        verifyNoInteractions(builds, activations);
    }

    @Test
    void firstStartup_waitsForBuildAndActivationToComplete() {
        when(builds.register(any())).thenReturn(build);
        when(builds.getJob(build.id())).thenReturn(build,
                build.start(0, now).beginVerification(now).succeed(0, 0, now));
        when(activations.register(any())).thenReturn(activation);
        when(activations.getJob(activation.id())).thenReturn(activation,
                activation.start(now).beginSwitch(now).confirmActivation(versionId, now));
        initializer(5000).run(new DefaultApplicationArguments());
        verify(builds).register(new RegisterProductSearchIndexBuildCommand("system-initial-load", "initial-load", 500, true));
        verify(activations).register(new RegisterProductSearchIndexActivationCommand(versionId, null,
                "system-initial-load", "initial-load-" + versionId, true));
    }

    @Test
    void failedBuild_throwsAndDoesNotActivate() {
        when(builds.register(any())).thenReturn(build);
        when(builds.getJob(build.id())).thenReturn(build.fail("INDEX_BUILD_FAILED", "OpenSearch unavailable", now));
        assertThatThrownBy(() -> initializer(1000).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(build.id().toString(), "INDEX_BUILD_FAILED", "OpenSearch unavailable");
        verifyNoInteractions(activations);
    }

    @Test
    void unconfirmedActivation_throwsInsteadOfCompletingStartup() {
        when(builds.register(any())).thenReturn(build);
        when(builds.getJob(build.id())).thenReturn(build.start(0, now).beginVerification(now).succeed(0, 0, now));
        when(activations.register(any())).thenReturn(activation);
        when(activations.getJob(activation.id())).thenReturn(activation.start(now).beginSwitch(now)
                .markUnconfirmed("ALIAS_UNCONFIRMED", "Alias response timed out", now));
        assertThatThrownBy(() -> initializer(1000).run(new DefaultApplicationArguments()))
                .hasMessageContaining(activation.id().toString(), "UNCONFIRMED");
    }

    @Test
    void timeout_throwsWithJobId() {
        when(builds.register(any())).thenReturn(build);
        when(builds.getJob(build.id())).thenReturn(build);
        assertThatThrownBy(() -> initializer(5).run(new DefaultApplicationArguments()))
                .hasMessageContaining("timed out", build.id().toString());
        verifyNoInteractions(activations);
    }

    @Test
    void interruptedWait_restoresInterruptAndFailsStartup() {
        when(builds.register(any())).thenReturn(build);
        when(builds.getJob(build.id())).thenReturn(build);
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> initializer(5000).run(new DefaultApplicationArguments())).hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void disabledSearchOrInitialLoad_doesNotRegisterInitializer() {
        for (String disabled : new String[]{"enabled", "initial-load.enabled"}) {
            new ApplicationContextRunner().withUserConfiguration(ProductSearchInitializer.class)
                    .withPropertyValues("product-service.search." + disabled + "=false")
                    .run(context -> assertThat(context).doesNotHaveBean(ProductSearchInitializer.class));
        }
    }

    @Test
    void enabledInitialLoad_withDisabledWorker_failsConfiguration() {
        assertThatThrownBy(() -> new ProductSearchInitializer(metadata, builds, activations, 500, 1000, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("enabled workers");
    }

    private ProductSearchInitializer initializer(long timeout) {
        return new ProductSearchInitializer(metadata, builds, activations, 500, timeout, true);
    }
}
