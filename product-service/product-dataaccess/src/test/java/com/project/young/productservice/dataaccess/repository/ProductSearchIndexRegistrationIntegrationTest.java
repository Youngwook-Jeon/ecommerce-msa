package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexMappingProperties;
import com.project.young.productservice.dataaccess.mapper.ProductSearchIndexMetadataMapper;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexBuildCommand;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.service.AdminProductSearchIndexService;
import com.project.young.productservice.dataaccess.adapter.ProductSearchIndexMetadataAdapter;
import com.project.young.productservice.dataaccess.adapter.ProductSearchIndexBuildWorkAdapter;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildWorkPort;
import com.project.young.productservice.application.exception.ProductSearchIndexOwnershipLostException;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildStatus;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;
import java.time.Duration;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.project.young.productservice.application.service.ProductSearchIndexBuildExecutor;
import com.project.young.productservice.dataaccess.search.ProductSearchIndexBatchReader;
import com.project.young.productservice.dataaccess.search.ProductSearchIndexVerificationReader;
import com.project.young.productservice.dataaccess.mapper.OpenSearchProductDocumentMapper;
import com.project.young.productservice.dataaccess.search.ProductSearchInitialLoader;
import com.project.young.productservice.dataaccess.search.OpenSearchProductBulkWriter;
import com.project.young.productservice.dataaccess.search.OpenSearchProductIndexBuildExecutionAdapter;
import com.project.young.productservice.dataaccess.search.CrossFieldsProductQueryBuilder;
import com.project.young.productservice.dataaccess.config.OpenSearchProductProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import java.net.URI;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = ProductSearchIndexRegistrationIntegrationTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductSearchIndexRegistrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb").withUsername("testuser").withPassword("testpass");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private ProductSearchIndexVersionJpaRepository versions;
    @Autowired
    private ProductSearchIndexBuildJobJpaRepository jobs;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminProductSearchIndexService service;

    @Autowired
    private ProductSearchIndexBuildWorkPort workStore;

    @Autowired
    private ProductSearchIndexBatchReader batchReader;
    @Autowired
    private ProductSearchIndexVerificationReader verificationReader;
    @Autowired
    private ProductSearchIndexMappingProperties mappingProperties;

    @BeforeEach
    void resetMetadata() {
        jdbcTemplate.execute("TRUNCATE product_search_index_build_jobs, product_search_index_versions CASCADE");
    }

    @Test
    void register_withValidRequest_commitsBothRowsWithUuidV7AndMappingHash() throws Exception {
        var job = service.register(command("request-1", 500));
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(job.id().version()).isEqualTo(7);
        assertThat(job.indexVersionId().version()).isEqualTo(7);
        assertThat(service.getJob(job.id())).isEqualTo(job);
        var page = service.listVersions(0, 20);
        assertThat(page.totalElements()).isEqualTo(1);
        try (var input = mappingProperties.resource().getInputStream()) {
            assertThat(page.content().getFirst().mappingHash()).isEqualTo(HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
        }
        assertThat(page.content().getFirst().indexName()).isEqualTo("products-v" + page.content().getFirst().versionNumber());
        assertThat(service.listVersions(10, 20).content()).isEmpty();
        assertThat(service.listVersions(10, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void repeatedRequest_beforeAndAfterFailure_returnsSameJobAndRejectsDifferentPayload() {
        var first = service.register(command("request-1", 500));
        assertThat(service.register(command("request-1", 500)).id()).isEqualTo(first.id());
        assertCode(() -> service.register(command("request-1", 100)), "IDEMPOTENCY_KEY_CONFLICT");
        assertCode(() -> service.register(command("request-2", 500)), "INDEX_BUILD_IN_PROGRESS");
        var entity = jobs.findById(first.id()).orElseThrow();
        var failed = first.fail("INVALID_DOCUMENT", "문서 생성 실패", first.updatedAt());
        entity.setStatus(failed.status());
        entity.setFinishedAt(failed.finishedAt());
        entity.setFailureCode(failed.failureCode());
        entity.setFailureMessage(failed.failureMessage());
        jobs.saveAndFlush(entity);
        assertThat(service.register(command("request-1", 500)).status()).isEqualTo(failed.status());
        assertThat(versions.count()).isEqualTo(1);
    }

    @Test
    void concurrentSameRequest_returnsOneJobWithoutOrphanVersion() throws Exception {
        var results = concurrent("same", "same");
        assertThat(results.get(0)).isInstanceOf(ProductSearchIndexBuildJob.class);
        assertThat(results.get(1)).isEqualTo(results.get(0));
        assertThat(jobs.count()).isEqualTo(1);
        assertThat(versions.count()).isEqualTo(1);
    }

    @Test
    void concurrentDifferentRequests_acceptsOneAndReturnsConflictForOther() throws Exception {
        var results = concurrent("first", "second");
        assertThat(results.stream().filter(ProductSearchIndexBuildJob.class::isInstance).count()).isEqualTo(1);
        assertThat(results).contains("INDEX_BUILD_IN_PROGRESS");
        assertThat(jobs.count()).isEqualTo(1);
        assertThat(versions.count()).isEqualTo(1);
    }

    @Test
    void failedJobInsert_rollsBackNewIndexVersion() {
        jdbcTemplate.execute("ALTER TABLE product_search_index_build_jobs ADD CONSTRAINT test_rejected_request CHECK (idempotency_key <> 'rejected')");
        try {
            assertCode(() -> service.register(command("rejected", 500)), "INDEX_MANAGEMENT_UNAVAILABLE");
            assertThat(jobs.count()).isZero();
            assertThat(versions.count()).isZero();
        } finally {
            jdbcTemplate.execute("ALTER TABLE product_search_index_build_jobs DROP CONSTRAINT test_rejected_request");
        }
    }

    @Test
    void claimedJob_hasExclusiveOwnerAndCanPersistProgressBeforeReady() {
        var registered = service.register(command("work-1", 500));
        var work = workStore.claim(Duration.ofSeconds(60)).orElseThrow();
        assertThat(work.job().id()).isEqualTo(registered.id());
        assertThat(work.job().status()).isEqualTo(ProductSearchIndexBuildStatus.RUNNING);
        assertThat(work.version().status()).isEqualTo(ProductSearchIndexStatus.BUILDING);
        assertThat(workStore.claim(Duration.ofSeconds(60))).isEmpty();
        workStore.renew(work, Duration.ofSeconds(60));
        long count = work.job().sourceProductCount();
        // Seeded test catalog is smaller than the configured batch size.
        if (count > 0) {
            workStore.recordBatch(work, count);
        }
        workStore.beginVerification(work);
        workStore.succeed(work, count);
        assertThat(service.getJob(work.job().id()).status()).isEqualTo(ProductSearchIndexBuildStatus.SUCCEEDED);
        assertThat(versions.findById(work.version().id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.READY);
        assertThat(jobs.findById(work.job().id()).orElseThrow().getOwnerToken()).isNull();
    }

    @Test
    void expiredLease_interruptsWithoutReclaimAndFencesPreviousOwner() {
        service.register(command("expired", 500));
        var work = workStore.claim(Duration.ofSeconds(60)).orElseThrow();
        jdbcTemplate.update("UPDATE product_search_index_build_jobs SET lease_until = clock_timestamp() - interval '1 second' WHERE id = ?", work.job().id());
        assertThatThrownBy(() -> workStore.renew(work, Duration.ofSeconds(60))).isInstanceOf(ProductSearchIndexOwnershipLostException.class);
        workStore.recoverExpired();
        assertThat(service.getJob(work.job().id()).status()).isEqualTo(ProductSearchIndexBuildStatus.INTERRUPTED);
        assertThat(versions.findById(work.version().id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.FAILED);
        assertThat(workStore.claim(Duration.ofSeconds(60))).isEmpty();
        assertThatThrownBy(() -> workStore.recordBatch(work, 1)).isInstanceOf(ProductSearchIndexOwnershipLostException.class);
        assertThatThrownBy(() -> workStore.fail(work)).isInstanceOf(ProductSearchIndexOwnershipLostException.class);
    }

    @Test
    void wrongOwnerToken_cannotRenewOrChangeProgress() {
        service.register(command("owner", 500));
        var work = workStore.claim(Duration.ofSeconds(60)).orElseThrow();
        var wrong = new ProductSearchIndexBuildWorkPort.Work(work.job(), work.version(), UUID.randomUUID());
        assertThatThrownBy(() -> workStore.renew(wrong, Duration.ofSeconds(60))).isInstanceOf(ProductSearchIndexOwnershipLostException.class);
        assertThatThrownBy(() -> workStore.recordBatch(wrong, 1)).isInstanceOf(ProductSearchIndexOwnershipLostException.class);
        assertThat(service.getJob(work.job().id()).indexedProducts()).isZero();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENSEARCH_TEST_ENDPOINT", matches = ".+")
    void queuedBuild_withRealPostgresAndOpenSearch_reachesReadyWithoutReadAlias() throws Exception {
        var registered = service.register(command("full-build", 2));
        String index = "product-index-worker-test-" + UUID.randomUUID();
        jdbcTemplate.update("UPDATE product_search_index_versions SET index_name = ? WHERE id = ?", index, registered.indexVersionId());
        URI endpoint = URI.create(System.getenv("OPENSEARCH_TEST_ENDPOINT"));
        try (var transport = ApacheHttpClient5TransportBuilder.builder(HttpHost.create(endpoint))
                .setMapper(new JacksonJsonpMapper(new ObjectMapper())).build();
             var heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            var client = new OpenSearchClient(transport);
            var loader = new ProductSearchInitialLoader(batchReader, new OpenSearchProductBulkWriter(client));
            var execution = new OpenSearchProductIndexBuildExecutionAdapter(client, new ObjectMapper(), loader, verificationReader,
                    new CrossFieldsProductQueryBuilder(), new OpenSearchProductProperties(endpoint, index, 1000, 5000, 3, 10000), mappingProperties);
            try {
                new ProductSearchIndexBuildExecutor(workStore, execution, heartbeat, Duration.ofSeconds(60), Duration.ofSeconds(10)).runOnce();
                var completed = service.getJob(registered.id());
                assertThat(completed.status()).isEqualTo(ProductSearchIndexBuildStatus.SUCCEEDED);
                assertThat(completed.completedBatches()).isGreaterThan(0);
                assertThat(versions.findById(registered.indexVersionId()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.READY);
                assertThat(client.indices().get(g -> g.index(index)).result().get(index).aliases()).isEmpty();
                assertThat(client.count(c -> c.index(index)).count()).isEqualTo(completed.indexedProducts());
            } finally {
                if (client.indices().exists(e -> e.index(index)).value()) {
                    client.indices().delete(d -> d.index(index));
                }
            }
        }
    }

    @Test
    void verification_withMismatchedCount_canOnlyTerminateAsFailed() {
        service.register(command("bad-count", 500));
        var work = workStore.claim(Duration.ofSeconds(60)).orElseThrow();
        long count = work.job().sourceProductCount();
        if (count > 0) {
            workStore.recordBatch(work, count);
        }
        workStore.beginVerification(work);
        assertThatThrownBy(() -> workStore.succeed(work, count + 1)).isInstanceOf(IllegalStateException.class);
        assertThat(service.getJob(work.job().id()).status()).isEqualTo(ProductSearchIndexBuildStatus.VERIFYING);
        workStore.fail(work);
        assertThat(service.getJob(work.job().id()).status()).isEqualTo(ProductSearchIndexBuildStatus.FAILED);
        assertThat(versions.findById(work.version().id()).orElseThrow().getStatus()).isEqualTo(ProductSearchIndexStatus.FAILED);
    }

    private java.util.List<Object> concurrent(String firstKey, String secondKey) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attempt(firstKey, ready, start));
            var second = executor.submit(() -> attempt(secondKey, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private Object attempt(String key, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            return service.register(command(key, 500));
        } catch (ProductSearchIndexManagementException exception) {
            return exception.code().name();
        }
    }

    private static RegisterProductSearchIndexBuildCommand command(String key, int batchSize) {
        return new RegisterProductSearchIndexBuildCommand("admin-sub", key, batchSize, true);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ProductSearchIndexManagementException.class,
                exception -> assertThat(exception.code().name()).isEqualTo(code));
    }

    @Configuration
    @Import({ProductDataAccessConfig.class, ProductSearchIndexMetadataAdapter.class,
            ProductSearchIndexMetadataMapper.class, AdminProductSearchIndexService.class, ProductSearchIndexBuildWorkAdapter.class,
            ProductSearchIndexBatchReader.class, ProductSearchIndexVerificationReader.class, OpenSearchProductDocumentMapper.class})
    static class Config {
    }
}
