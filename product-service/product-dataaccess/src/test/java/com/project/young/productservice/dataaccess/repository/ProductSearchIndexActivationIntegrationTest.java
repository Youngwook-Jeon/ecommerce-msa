package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasState;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexActivationCommand;
import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexBuildCommand;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.exception.ProductSearchIndexOwnershipLostException;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationMetadataPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildWorkPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationStatus;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import com.project.young.productservice.application.service.AdminProductSearchIndexActivationService;
import com.project.young.productservice.application.service.ProductSearchIndexActivationExecutor;
import com.project.young.productservice.dataaccess.adapter.ProductSearchIndexActivationAdapter;
import com.project.young.productservice.dataaccess.adapter.ProductSearchIndexMetadataAdapter;
import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.search.ProductSearchCategoryNameReader;
import com.project.young.productservice.dataaccess.config.OpenSearchProductProperties;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexMappingProperties;
import com.project.young.productservice.dataaccess.mapper.ProductSearchIndexMetadataMapper;
import com.project.young.productservice.dataaccess.search.CrossFieldsProductQueryBuilder;
import com.project.young.productservice.dataaccess.search.OpenSearchProductIndexAliasAdapter;
import com.project.young.productservice.dataaccess.search.OpenSearchProductIndexBuildExecutionAdapter;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.project.young.productservice.application.port.output.ProductSearchIndexReconciliationPort;
import com.project.young.productservice.application.service.ProductSearchIndexActivationReconciler;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasObservation;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexReconciliationStatus;
import com.project.young.productservice.dataaccess.adapter.ProductSearchIndexReconciliationAdapter;
import com.project.young.productservice.dataaccess.mapper.ProductSearchIndexReconciliationMapper;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexReconciliationConfiguration;
import org.junit.jupiter.params.provider.EnumSource;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = ProductSearchIndexActivationIntegrationTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductSearchIndexActivationIntegrationTest {
    private static final String ALIAS = "product-activation-alias-test-" + UUID.randomUUID();
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb").withUsername("testuser").withPassword("testpass");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("product-service.search.index", () -> ALIAS);
    }

    @Autowired
    private ProductSearchIndexVersionJpaRepository versions;
    @Autowired
    private ProductSearchIndexActivationJobJpaRepository jobs;
    @Autowired
    private ProductSearchIndexActivationMetadataPort metadata;
    @Autowired
    private ProductSearchIndexActivationWorkPort store;
    @Autowired
    private ProductSearchIndexMetadataAdapter builds;
    @Autowired
    private AdminProductSearchIndexActivationService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private ProductSearchIndexMappingProperties mapping;
    @Autowired
    private ProductSearchCategoryNameReader categoryNames;
    @Autowired
    private ProductSearchIndexReconciliationPort reconciliation;
    @Autowired
    private ProductSearchIndexReconciliationAttemptJpaRepository attempts;
    @Autowired
    private ProductSearchIndexActivationReconciler reconciler;
    private final ProductSearchIndexMetadataMapper mapper = new ProductSearchIndexMetadataMapper();

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE product_search_index_versions CASCADE");
    }

    @Test
    void register_andReplay_preservesUuidV7AndRejectsChangedIntent() throws Exception {
        var target = ready(1);
        var command = command(target, null, "activate-1");
        var job = service.register(command);
        assertThat(job.id().version()).isEqualTo(7);
        assertThat(job.aliasName()).isEqualTo(ALIAS);
        assertThat(versions.findById(target.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.ACTIVATING);
        assertThat(service.register(command).id()).isEqualTo(job.id());
        assertCode(() -> service.register(command(readyUnchecked(2), null, "activate-1")), "IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void register_insideOuterTransaction_keepsAliasReadOutsideDatabaseTransaction() throws Exception {
        var target = ready(1);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            service.register(command(target, null, "activate-1"));
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            status.setRollbackOnly();
        });
        assertThat(jobs.count()).isEqualTo(1);
    }

    @Test
    void success_replacesActiveVersionInOneTransactionAndReplayStillWorks() throws Exception {
        var old = ready(1).beginActivation(Instant.now()).confirmActive(Instant.now());
        versions.saveAndFlush(mapper.toEntity(old, 0));
        var target = ready(2);
        var command = command(target, old.id(), "activate-1");
        var job = service.register(command);
        var work = store.claim(Duration.ofSeconds(60)).orElseThrow();
        store.beginSwitch(work);
        store.succeed(work);
        assertThat(service.getJob(job.id()).status()).isEqualTo(ProductSearchIndexActivationStatus.SUCCEEDED);
        assertThat(versions.findById(old.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.INACTIVE);
        assertThat(versions.findById(target.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.ACTIVE);
        assertThat(service.register(command).id()).isEqualTo(job.id());
    }

    @Test
    void successCommitFailure_rollsBackBothVersionsAndLeavesSwitchUnconfirmed() throws Exception {
        var old = ready(1).beginActivation(Instant.now()).confirmActive(Instant.now());
        versions.saveAndFlush(mapper.toEntity(old, 0));
        var target = ready(2);
        var job = service.register(command(target, old.id(), "activate-1"));
        var work = store.claim(Duration.ofSeconds(60)).orElseThrow();
        store.beginSwitch(work);
        // Force a failure at the final flush, after the old ACTIVE row has already been flushed as INACTIVE.
        jdbc.execute("ALTER TABLE product_search_index_versions ADD CONSTRAINT test_rejected_activation CHECK (status <> 'ACTIVE' OR version_number <> 2)");
        try {
            assertThatThrownBy(() -> store.succeed(work)).isInstanceOf(RuntimeException.class);
            assertThat(versions.findById(old.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.ACTIVE);
            assertThat(versions.findById(target.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.ACTIVATING);
            assertThat(service.getJob(job.id()).status()).isEqualTo(ProductSearchIndexActivationStatus.SWITCHING);
            store.failOrUnconfirm(work);
            assertThat(service.getJob(job.id()).status()).isEqualTo(ProductSearchIndexActivationStatus.UNCONFIRMED);
        } finally {
            jdbc.execute("ALTER TABLE product_search_index_versions DROP CONSTRAINT test_rejected_activation");
        }
    }

    @Test
    void failure_beforeSwitch_restoresReadyAndAllowsNewRequest() throws Exception {
        var target = ready(1);
        var job = service.register(command(target, null, "activate-1"));
        var work = store.claim(Duration.ofSeconds(60)).orElseThrow();
        store.failOrUnconfirm(work);
        assertThat(service.getJob(job.id()).status()).isEqualTo(ProductSearchIndexActivationStatus.FAILED);
        assertThat(versions.findById(target.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.READY);
        assertThat(service.register(command(target, null, "activate-2")).id()).isNotEqualTo(job.id());
    }

    @Test
    void failure_afterSwitchIntent_keepsActivatingAndBlocksActivationAndBuild() throws Exception {
        var target = ready(1);
        var job = service.register(command(target, null, "activate-1"));
        var work = store.claim(Duration.ofSeconds(60)).orElseThrow();
        store.beginSwitch(work);
        store.failOrUnconfirm(work);
        assertThat(service.getJob(job.id()).status()).isEqualTo(ProductSearchIndexActivationStatus.UNCONFIRMED);
        assertThat(versions.findById(target.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.ACTIVATING);
        assertCode(() -> metadata.register(command(readyUnchecked(2), null, "activate-2")), "INDEX_ACTIVATION_IN_PROGRESS");
        assertCode(() -> builds.register(new RegisterProductSearchIndexBuildCommand("admin", "build-1", 500, true)), "INDEX_ACTIVATION_IN_PROGRESS");
        assertThat(store.claim(Duration.ofSeconds(60))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void expiredLease_distinguishesPreSwitchFailureFromUnconfirmedSwitch(boolean switched) throws Exception {
        var target = ready(1);
        var job = service.register(command(target, null, "activate-1"));
        var work = store.claim(Duration.ofSeconds(60)).orElseThrow();
        if (switched) { store.beginSwitch(work); }
        jdbc.update("UPDATE product_search_index_activation_jobs SET lease_until = clock_timestamp() - interval '1 second' WHERE id = ?", job.id());
        store.recoverExpired();
        assertThat(service.getJob(job.id()).status()).isEqualTo(switched ? ProductSearchIndexActivationStatus.UNCONFIRMED : ProductSearchIndexActivationStatus.FAILED);
        assertThatThrownBy(() -> store.renew(work, Duration.ofSeconds(60))).isInstanceOf(ProductSearchIndexOwnershipLostException.class);
        assertThatThrownBy(() -> store.succeed(work)).isInstanceOf(ProductSearchIndexOwnershipLostException.class);
    }

    @Test
    void wrongOwner_cannotChangeActivation() throws Exception {
        var target = ready(1);
        service.register(command(target, null, "activate-1"));
        var work = store.claim(Duration.ofSeconds(60)).orElseThrow();
        var wrong = new ProductSearchIndexActivationWorkPort.Work(work.job(), work.version(), work.expectedIndexName(), UUID.randomUUID());
        assertThatThrownBy(() -> store.beginSwitch(wrong)).isInstanceOf(ProductSearchIndexOwnershipLostException.class);
        assertThatThrownBy(() -> store.failOrUnconfirm(wrong)).isInstanceOf(ProductSearchIndexOwnershipLostException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentRegistrations_replaySameKeyOrAcceptOnlyOneDifferentKey(boolean sameKey) throws Exception {
        var target = ready(1);
        var second = sameKey ? target : ready(2);
        var start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> { start.await(); return registerOrConflict(command(target, null, "activate-1")); });
            var other = threads.submit(() -> { start.await(); return registerOrConflict(command(second, null, sameKey ? "activate-1" : "activate-2")); });
            start.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), other.get(10, TimeUnit.SECONDS));
            assertThat(jobs.count()).isEqualTo(1);
            if (sameKey) { assertThat(results.getFirst()).isEqualTo(results.getLast()); }
            else { assertThat(results.stream().filter(UUID.class::isInstance)).hasSize(1); }
        }
    }

    @Test
    void registration_withQueuedBuild_rejectsBeforeIndexMutation() throws Exception {
        var target = ready(100);
        builds.register(new RegisterProductSearchIndexBuildCommand("admin", "build-1", 500, true));
        assertCode(() -> metadata.register(command(target, null, "activate-1")), "INDEX_BUILD_IN_PROGRESS");
        assertThat(versions.findById(target.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.READY);
    }

    @ParameterizedTest
    @EnumSource(value = ProductSearchIndexAliasObservation.class, names = {"PREVIOUS_OBSERVED", "ALIAS_ABSENT", "MULTIPLE_INDICES", "UNEXPECTED_INDEX", "ALIAS_OPTIONS_MISMATCH", "LOOKUP_FAILED"})
    void reconciliation_nonTargetObservation_keepsBlockAndAuditsResult(ProductSearchIndexAliasObservation observation) throws Exception {
        var old = ready(1).beginActivation(Instant.now()).confirmActive(Instant.now());
        versions.saveAndFlush(mapper.toEntity(old, 0));
        var target = ready(2);
        var job = unconfirmed(target, old.id());
        var work = reconciliation.claim(job.id(), "operator", Duration.ofSeconds(60));
        ProductSearchIndexAliasState state = switch (observation) {
            case PREVIOUS_OBSERVED -> new ProductSearchIndexAliasState(Set.of(old.indexName()), true);
            case ALIAS_ABSENT -> new ProductSearchIndexAliasState(Set.of(), true);
            case MULTIPLE_INDICES -> new ProductSearchIndexAliasState(Set.of(old.indexName(), target.indexName()), true);
            case UNEXPECTED_INDEX -> new ProductSearchIndexAliasState(Set.of("other-index"), true);
            case ALIAS_OPTIONS_MISMATCH -> new ProductSearchIndexAliasState(Set.of(target.indexName()), false);
            case LOOKUP_FAILED -> null;
            default -> throw new IllegalArgumentException("Unexpected test observation");
        };
        var result = reconciliation.finish(work, state);
        assertThat(result.observation()).isEqualTo(observation);
        assertThat(result.status()).isEqualTo(state == null ? ProductSearchIndexReconciliationStatus.FAILED : ProductSearchIndexReconciliationStatus.UNRESOLVED);
        assertThat(result.requestedBy()).isEqualTo("operator");
        assertThat(reconciliation.latest(job.id())).contains(result);
        assertThat(service.getJob(job.id()).status()).isEqualTo(ProductSearchIndexActivationStatus.UNCONFIRMED);
        assertThat(versions.findById(target.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.ACTIVATING);
        assertThat(reconciliation.claimNext(Duration.ofSeconds(60), Duration.ofSeconds(60))).isEmpty();
        assertCode(() -> builds.register(new RegisterProductSearchIndexBuildCommand("admin", "build-new", 500, true)), "INDEX_ACTIVATION_IN_PROGRESS");
        assertThat(reconciliation.claim(job.id(), "operator", Duration.ofSeconds(60)).attemptId()).isNotEqualTo(work.attemptId());
    }

    @Test
    void reconciliation_targetConfirmed_updatesJobVersionsAndAuditAtomically() throws Exception {
        var old = ready(1).beginActivation(Instant.now()).confirmActive(Instant.now());
        versions.saveAndFlush(mapper.toEntity(old, 0));
        var target = ready(2);
        var job = unconfirmed(target, old.id());
        var work = reconciliation.claimNext(Duration.ofSeconds(60), Duration.ofSeconds(60)).orElseThrow();
        assertThat(work.attemptId().version()).isEqualTo(7);
        var result = reconciliation.finish(work, new ProductSearchIndexAliasState(Set.of(target.indexName()), true));
        assertThat(result.status()).isEqualTo(ProductSearchIndexReconciliationStatus.SUCCEEDED);
        assertThat(result.requestedBy()).isNull();
        assertThat(service.getJob(job.id()).status()).isEqualTo(ProductSearchIndexActivationStatus.SUCCEEDED);
        assertThat(service.getJob(job.id()).failureCode()).isNull();
        assertThat(versions.findById(old.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.INACTIVE);
        assertThat(versions.findById(target.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.ACTIVE);
        assertThat(reconciliation.claimNext(Duration.ofSeconds(60), Duration.ofSeconds(60))).isEmpty();
    }

    @Test
    void reconciliation_expiredIndependentOwnership_rejectsLateResultAndAllowsNewClaim() throws Exception {
        var target = ready(1);
        var job = unconfirmed(target, null);
        var work = reconciliation.claim(job.id(), "operator", Duration.ofSeconds(60));
        jdbc.update("UPDATE product_search_index_reconciliation_attempts SET created_at = clock_timestamp() - interval '2 minutes', lease_until = clock_timestamp() - interval '1 minute' WHERE id = ?", work.attemptId());
        assertThatThrownBy(() -> reconciliation.finish(work, new ProductSearchIndexAliasState(Set.of(target.indexName()), true)))
                .isInstanceOf(ProductSearchIndexOwnershipLostException.class);
        reconciliation.recoverExpired();
        assertThat(reconciliation.latest(job.id()).orElseThrow().status()).isEqualTo(ProductSearchIndexReconciliationStatus.EXPIRED);
        assertThat(reconciliation.claim(job.id(), "operator", Duration.ofSeconds(60)).attemptId()).isNotEqualTo(work.attemptId());
    }

    @Test
    void reconciliation_stateChangedDuringLookup_auditsStaleWithoutActivating() throws Exception {
        var target = ready(1);
        var job = unconfirmed(target, null);
        var work = reconciliation.claim(job.id(), "operator", Duration.ofSeconds(60));
        jdbc.update("UPDATE product_search_index_activation_jobs SET lock_version = lock_version + 1 WHERE id = ?", job.id());
        var result = reconciliation.finish(work, new ProductSearchIndexAliasState(Set.of(target.indexName()), true));
        assertThat(result.status()).isEqualTo(ProductSearchIndexReconciliationStatus.STALE);
        assertThat(service.getJob(job.id()).status()).isEqualTo(ProductSearchIndexActivationStatus.UNCONFIRMED);
        assertThat(versions.findById(target.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.ACTIVATING);
    }

    @Test
    void reconciliation_commitFailure_rollsBackJobVersionsAndObservation() throws Exception {
        var old = ready(1).beginActivation(Instant.now()).confirmActive(Instant.now());
        versions.saveAndFlush(mapper.toEntity(old, 0));
        var target = ready(2);
        var job = unconfirmed(target, old.id());
        var work = reconciliation.claim(job.id(), "operator", Duration.ofSeconds(60));
        jdbc.execute("ALTER TABLE product_search_index_versions ADD CONSTRAINT test_rejected_reconciliation CHECK (status <> 'ACTIVE' OR version_number <> 2)");
        try {
            assertThatThrownBy(() -> reconciliation.finish(work, new ProductSearchIndexAliasState(Set.of(target.indexName()), true)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(versions.findById(old.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.ACTIVE);
            assertThat(versions.findById(target.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.ACTIVATING);
            assertThat(service.getJob(job.id()).status()).isEqualTo(ProductSearchIndexActivationStatus.UNCONFIRMED);
            var attempt = reconciliation.latest(job.id()).orElseThrow();
            assertThat(attempt.status()).isEqualTo(ProductSearchIndexReconciliationStatus.RUNNING);
            assertThat(attempt.observation()).isNull();
        } finally {
            jdbc.execute("ALTER TABLE product_search_index_versions DROP CONSTRAINT test_rejected_reconciliation");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void manualReconciliation_validActivationLease_isNotTouched(boolean switching) throws Exception {
        var target = ready(1);
        var job = service.register(command(target, null, "activate-1"));
        var work = store.claim(Duration.ofSeconds(60)).orElseThrow();
        if (switching) { store.beginSwitch(work); }
        assertCode(() -> reconciler.recheck(job.id(), "operator"), "INDEX_ACTIVATION_NOT_UNCONFIRMED");
        assertThat(attempts.count()).isZero();
        store.renew(work, Duration.ofSeconds(60));
    }

    @Test
    void manualReconciliation_insideOuterTransaction_observesOutsideTransaction() throws Exception {
        var target = ready(1);
        var job = unconfirmed(target, null);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var result = reconciler.recheck(job.id(), "operator");
            assertThat(result.observation()).isEqualTo(ProductSearchIndexAliasObservation.ALIAS_ABSENT);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            status.setRollbackOnly();
        });
        assertThat(reconciliation.latest(job.id())).isPresent();
    }

    @Test
    void concurrentReconciliationClaims_onlyOneObserverOwnsTheJob() throws Exception {
        var target = ready(1);
        var job = unconfirmed(target, null);
        var start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> { start.await(); return claimReconciliationOrConflict(job.id()); });
            var other = threads.submit(() -> { start.await(); return claimReconciliationOrConflict(job.id()); });
            start.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), other.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(UUID.class::isInstance)).hasSize(1);
            assertThat(results).contains("INDEX_RECONCILIATION_IN_PROGRESS");
            assertThat(attempts.count()).isEqualTo(1);
        }
    }

    private Object claimReconciliationOrConflict(UUID jobId) {
        try { return reconciliation.claim(jobId, "operator", Duration.ofSeconds(60)).attemptId(); }
        catch (ProductSearchIndexManagementException exception) { return exception.code().name(); }
    }

    private ProductSearchIndexActivationJob unconfirmed(ProductSearchIndexVersion target, UUID previous) {
        var job = service.register(command(target, previous, "activate-1"));
        var work = store.claim(Duration.ofSeconds(60)).orElseThrow();
        store.beginSwitch(work);
        store.failOrUnconfirm(work);
        return service.getJob(job.id());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENSEARCH_TEST_ENDPOINT", matches = ".+")
    void realOpenSearch_lostReplacementResponse_reconcilesAliasAndDatabase() throws Exception {
        var first = ready(1);
        var second = ready(2);
        URI endpoint = URI.create(System.getenv("OPENSEARCH_TEST_ENDPOINT"));
        try (var transport = ApacheHttpClient5TransportBuilder.builder(HttpHost.create(endpoint))
                .setHttpClientConfigCallback(c -> c.disableAutomaticRetries()).setMapper(new JacksonJsonpMapper(new ObjectMapper())).build();
             var heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            var client = new OpenSearchClient(transport);
            var creator = new OpenSearchProductIndexBuildExecutionAdapter(client, new ObjectMapper(), null, null,
                    new CrossFieldsProductQueryBuilder(), new OpenSearchProductProperties(endpoint, ALIAS, 1000, 5000, 3, 10000), mapping, categoryNames);
            var aliases = new OpenSearchProductIndexAliasAdapter(client, mapping);
            var actualService = new AdminProductSearchIndexActivationService(metadata, aliases);
            var worker = new ProductSearchIndexActivationExecutor(store, aliases, heartbeat, Duration.ofSeconds(60), Duration.ofSeconds(10));
            try {
                creator.create(new ProductSearchIndexBuildWorkPort.Work(null, first, null));
                creator.create(new ProductSearchIndexBuildWorkPort.Work(null, second, null));
                var firstJob = actualService.register(command(first, null, "activate-1"));
                worker.runOnce();
                assertThat(actualService.getJob(firstJob.id()).status()).isEqualTo(ProductSearchIndexActivationStatus.SUCCEEDED);
                var secondJob = actualService.register(command(second, first.id(), "activate-2"));
                var activationWork = store.claim(Duration.ofSeconds(60)).orElseThrow();
                aliases.verifyBeforeSwitch(activationWork);
                store.beginSwitch(activationWork);
                aliases.switchAlias(activationWork);
                // Simulate a lost response after the actual switch was applied.
                store.failOrUnconfirm(activationWork);
                var actualReconciler = new ProductSearchIndexActivationReconciler(reconciliation, store, aliases,
                        Duration.ofSeconds(60), Duration.ofSeconds(60));
                assertThat(actualReconciler.recheck(secondJob.id(), "operator").status())
                        .isEqualTo(ProductSearchIndexReconciliationStatus.SUCCEEDED);
                assertThat(actualService.getJob(secondJob.id()).status()).isEqualTo(ProductSearchIndexActivationStatus.SUCCEEDED);
                assertThat(aliases.readAlias(ALIAS).indices()).containsExactly(second.indexName());
                assertThat(versions.findById(first.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.INACTIVE);
                assertThat(versions.findById(second.id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.ACTIVE);
                assertThat(client.count(c -> c.index(ALIAS)).count()).isZero();
            } finally {
                for (var version : List.of(first, second)) {
                    if (client.indices().exists(e -> e.index(version.indexName())).value()) {
                        client.indices().delete(d -> d.index(version.indexName()));
                    }
                }
            }
        }
    }

    private Object registerOrConflict(RegisterProductSearchIndexActivationCommand command) {
        try { return service.register(command).id(); }
        catch (ProductSearchIndexManagementException exception) { return exception.code().name(); }
    }

    private ProductSearchIndexVersion readyUnchecked(long number) {
        try { return ready(number); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private ProductSearchIndexVersion ready(long number) throws Exception {
        String hash;
        try (var input = mapping.resource().getInputStream()) {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
        Instant now = Instant.now();
        var version = ProductSearchIndexVersion.register(UUID.randomUUID(), number, ProductSearchIndexMappingProperties.CURRENT_VERSION, hash, "admin", now).beginBuild(now).markReady(now);
        var entity = mapper.toEntity(version, 0);
        entity.setIndexName("product-activation-test-" + UUID.randomUUID());
        return mapper.toDomain(versions.saveAndFlush(entity));
    }

    private static RegisterProductSearchIndexActivationCommand command(ProductSearchIndexVersion target, UUID previous, String key) {
        return new RegisterProductSearchIndexActivationCommand(target.id(), previous, "admin", key, true);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ProductSearchIndexManagementException.class,
                exception -> assertThat(exception.code().name()).isEqualTo(code));
    }

    @Configuration
    @Import({ProductDataAccessConfig.class, ProductSearchCategoryNameReader.class, ProductSearchIndexActivationAdapter.class, ProductSearchIndexMetadataAdapter.class,
            ProductSearchIndexMetadataMapper.class, AdminProductSearchIndexActivationService.class,
            ProductSearchIndexReconciliationAdapter.class, ProductSearchIndexReconciliationMapper.class, ProductSearchIndexReconciliationConfiguration.class})
    static class Config {
        @Bean
        ProductSearchIndexAliasPort aliases(ProductSearchIndexVersionJpaRepository versions) {
            return new ProductSearchIndexAliasPort() {
                public ProductSearchIndexAliasState readAlias(String aliasName) {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return new ProductSearchIndexAliasState(versions.findByStatus(ProductSearchIndexStatus.ACTIVE)
                            .map(v -> Set.of(v.getIndexName())).orElse(Set.of()), true);
                }
                public void verifyBeforeSwitch(ProductSearchIndexActivationWorkPort.Work work) { throw new UnsupportedOperationException(); }
                public void switchAlias(ProductSearchIndexActivationWorkPort.Work work) { throw new UnsupportedOperationException(); }
                public void verifyAfterSwitch(ProductSearchIndexActivationWorkPort.Work work) { throw new UnsupportedOperationException(); }
            };
        }
    }
}
