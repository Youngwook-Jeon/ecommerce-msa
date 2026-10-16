package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.dataaccess.catalog.ProductCatalogRevisionStore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.event.ProductCatalogChangedEvent;
import com.project.young.productservice.application.dto.event.ProductCatalogChangeType;
import com.project.young.productservice.application.exception.ProductSearchSynchronizationDeferredException;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildWorkPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import com.project.young.productservice.dataaccess.adapter.ProductCatalogOutboxAdapter;
import com.project.young.productservice.dataaccess.adapter.ProductRepositoryImpl;
import com.project.young.productservice.dataaccess.mapper.ProductAggregateMapper;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexMappingProperties;
import com.project.young.productservice.dataaccess.config.OpenSearchProductProperties;
import com.project.young.productservice.dataaccess.mapper.OpenSearchProductDocumentMapper;
import com.project.young.productservice.dataaccess.mapper.ProductSearchIndexMetadataMapper;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexVersionJpaRepository;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = ProductSearchSynchronizationIntegrationTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductSearchSynchronizationIntegrationTest {
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
    }

    @Autowired private ProductRepository products;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ProductCatalogRevisionStore revisions;
    @Autowired private ProductCatalogOutboxAdapter outbox;
    @Autowired private ProductSearchSynchronizationReader reader;
    @Autowired private ProductSearchSynchronizationTargetReader targets;
    @Autowired private ProductSearchIndexVersionJpaRepository versions;
    @Autowired private ProductSearchCategoryNameReader categoryNames;
    @Autowired private ProductSearchIndexMappingProperties mapping;
    private UUID productId;
    private long categoryId;

    @BeforeEach
    void createProduct() {
        productId = UUID.randomUUID();
        categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE status = 'ACTIVE' ORDER BY id LIMIT 1", Long.class);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            products.insert(newProduct(productId, "initial"));
            emit(ProductCatalogChangeType.PRODUCT_CREATED);
        });
    }

    private Product newProduct(UUID id, String name) {
        return Product.builder().productId(new ProductId(id)).categoryId(new CategoryId(categoryId))
                .name(name).description("Test product description for revision synchronization").brand("test brand").basePrice(new Money(BigDecimal.TEN)).status(ProductStatus.ACTIVE)
                .conditionType(ConditionType.NEW).mainImageUrl("https://example.com/product.jpg").build();
    }

    @Test
    void productCreation_initializesRevisionInSameTransaction_andRollsBackTogether() {
        var id = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            products.insert(newProduct(id, "rolled back creation"));
            assertThat(revisions.read(List.of(id))).containsEntry(id, 1L);
            status.setRollbackOnly();
        });
        assertThat(products.findById(new ProductId(id))).isEmpty();
        assertThat(revisions.read(List.of(id))).isEmpty();
    }

    @Test
    void versionCheck_missingRevisionOfExistingProduct_failsWithoutRepairingState() {
        jdbc.update("DELETE FROM product_catalog_versions WHERE product_id = ?", productId);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> revisions.verifyVersion(productId, 1L)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("revision is missing");
        assertThat(revisions.read(List.of(productId))).isEmpty();
    }

    @Test
    void updateLookup_nonexistentProduct_returnsEmptyWithoutCreatingRevision() {
        var id = UUID.randomUUID();
        var found = new TransactionTemplate(transactions).execute(status -> products.findById(new ProductId(id)));
        assertThat(found).isEmpty();
        assertThat(revisions.read(List.of(id))).isEmpty();
    }

    @Test
    void mutation_commitsProductRevisionHistoryAndOutboxTogether() {
        change("UPDATE products SET name = 'updated' WHERE id = ?", ProductCatalogChangeType.PRODUCT_UPDATED);
        var document = reader.read(productId, 1L);
        assertThat(document.name()).isEqualTo("updated");
        assertThat(document.catalogRevision()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT revision FROM product_catalog_change_history WHERE product_id = ? ORDER BY revision", Long.class, productId))
                .containsExactly(1L, 2L);
        assertThat(jdbc.queryForList("SELECT catalog_revision FROM product_catalog_outbox WHERE product_id = ? ORDER BY catalog_revision", Long.class, productId))
                .containsExactly(1L, 2L);
        assertThat(jdbc.queryForObject("SELECT changed_by FROM product_catalog_change_history WHERE product_id = ? AND revision = 2", String.class, productId)).isEqualTo("SYSTEM");
    }

    @Test
    void rollback_discardsMutationRevisionHistoryAndOutbox() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            revisions.verifyVersion(productId, revisions.current(productId));
            jdbc.update("UPDATE products SET name = 'rolled back' WHERE id = ?", productId);
            emit(ProductCatalogChangeType.PRODUCT_UPDATED);
            status.setRollbackOnly();
        });
        assertThat(reader.read(productId, null).name()).isEqualTo("initial");
        assertThat(reader.read(productId, null).catalogRevision()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_catalog_change_history WHERE product_id = ?", Long.class, productId)).isEqualTo(1);
    }

    @Test
    void outboxCommitFailure_rollsBackRevisionHistoryAndProduct() {
        jdbc.execute("ALTER TABLE product_catalog_outbox ADD CONSTRAINT test_rejected_sync CHECK (product_id <> '" + productId + "'::uuid OR catalog_revision <> 2)");
        try {
            assertThatThrownBy(() -> change("UPDATE products SET name = 'not committed' WHERE id = ?", ProductCatalogChangeType.PRODUCT_UPDATED))
                    .isInstanceOf(RuntimeException.class);
            assertThat(reader.read(productId, null).name()).isEqualTo("initial");
            assertThat(reader.read(productId, null).catalogRevision()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM product_catalog_change_history WHERE product_id = ?", Long.class, productId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM product_catalog_outbox WHERE product_id = ?", Long.class, productId)).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE product_catalog_outbox DROP CONSTRAINT test_rejected_sync");
        }
    }

    @Test
    void dataWriteFailure_afterVersionAndOutbox_rollsBackAllMetadata() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            outbox.enqueue(new ProductCatalogChangedEvent(UUID.randomUUID(), productId, null,
                    ProductCatalogChangeType.PRODUCT_UPDATED, Instant.now(), 1L));
            jdbc.update("UPDATE products SET name = NULL WHERE id = ?", productId);
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(revisions.current(productId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_catalog_change_history WHERE product_id = ?", Long.class, productId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_catalog_outbox WHERE product_id = ?", Long.class, productId)).isEqualTo(1);
    }

    @Test
    void concurrentProductChanges_sameExpectedVersion_onlyOneCommits() throws Exception {
        var read = new CountDownLatch(2);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> changeConcurrently(read, "UPDATE products SET name = 'one' WHERE id = ?"));
            var second = threads.submit(() -> changeConcurrently(read, "UPDATE products SET base_price = 20 WHERE id = ?"));
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        var document = reader.read(productId, 2L);
        assertThat(document.catalogRevision()).isEqualTo(2);
        assertThat(document.name().equals("one") ^ document.basePrice().compareTo(new BigDecimal("20")) == 0).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_catalog_change_history WHERE product_id = ?", Long.class, productId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_catalog_outbox WHERE product_id = ?", Long.class, productId)).isEqualTo(2);
    }

    private boolean changeConcurrently(CountDownLatch read, String sql) {
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                revisions.verifyVersion(productId, 1L);
                read.countDown();
                try {
                    if (!read.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Concurrent reads timed out"); }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                outbox.enqueue(new ProductCatalogChangedEvent(UUID.randomUUID(), productId, null,
                        ProductCatalogChangeType.PRODUCT_UPDATED, Instant.now(), 1L));
                jdbc.update(sql, productId);
            });
            return true;
        } catch (com.project.young.productservice.application.exception.ProductVersionConflictException conflict) {
            return false;
        }
    }

    @Test
    void incompleteProduct_keepsRevisionAndIsNotSearchable() {
        change("UPDATE products SET category_id = NULL WHERE id = ?", ProductCatalogChangeType.PRODUCT_UPDATED);
        var document = reader.read(productId, 2L);
        assertThat(document.documentReady()).isFalse();
        assertThat(document.categoryId()).isNull();
        assertThat(document.catalogRevision()).isEqualTo(2);
        assertThatThrownBy(() -> reader.read(productId, 3L)).isInstanceOf(ProductSearchSynchronizationDeferredException.class);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENSEARCH_TEST_ENDPOINT", matches = ".+")
    void realOpenSearch_updatesHidesAndRestoresWithoutStaleResurrection() throws Exception {
        String index = "product-sync-test-" + UUID.randomUUID();
        String alias = index + "-read";
        URI endpoint = URI.create(System.getenv("OPENSEARCH_TEST_ENDPOINT"));
        String hash;
        try (var input = mapping.resource().getInputStream()) {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
        Instant now = Instant.now();
        var version = new ProductSearchIndexVersion(UUID.randomUUID(), System.nanoTime(), index, 1, hash,
                com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus.ACTIVE, "test", now, now, now);
        versions.saveAndFlush(new ProductSearchIndexMetadataMapper().toEntity(version, 0));
        try (var transport = ApacheHttpClient5TransportBuilder.builder(HttpHost.create(endpoint))
                .setHttpClientConfigCallback(c -> c.disableAutomaticRetries()).setMapper(new JacksonJsonpMapper(new ObjectMapper())).build()) {
            var client = new OpenSearchClient(transport);
            var properties = new OpenSearchProductProperties(endpoint, alias, 1000, 5000, 3, 10000);
            var writer = new OpenSearchProductBulkWriter(client);
            var aliases = new OpenSearchProductIndexAliasAdapter(client, mapping);
            var synchronization = new OpenSearchProductSynchronizationAdapter(reader, targets, writer, aliases, properties);
            try {
                new OpenSearchProductIndexBuildExecutionAdapter(client, new ObjectMapper(), null, null,
                        new CrossFieldsProductQueryBuilder(), properties, mapping, categoryNames)
                        .create(new ProductSearchIndexBuildWorkPort.Work(null, version, null));
                client.indices().putAlias(r -> r.index(index).name(alias).isWriteIndex(false));
                var original = reader.read(productId, 1L);
                synchronization.synchronize(productId, 1L);
                change("UPDATE products SET name = 'latest' WHERE id = ?", ProductCatalogChangeType.PRODUCT_UPDATED);
                synchronization.synchronize(productId, 1L); // An old event still reads revision 2.
                writer.write(index, List.of(original)); // Late revision 1 cannot overwrite revision 2.
                var stored = client.get(g -> g.index(index).id(productId.toString()), OpenSearchProductDocument.class);
                assertThat(stored.source().name()).isEqualTo("latest");
                assertThat(stored.version()).isEqualTo(2);
                change("UPDATE products SET category_id = NULL WHERE id = ?", ProductCatalogChangeType.PRODUCT_UPDATED);
                synchronization.synchronize(productId, 3L);
                writer.write(index, List.of(original));
                assertThat(client.get(g -> g.index(index).id(productId.toString()), OpenSearchProductDocument.class).source().documentReady()).isFalse();
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    revisions.verifyVersion(productId, revisions.current(productId));
                    jdbc.update("UPDATE products SET category_id = ? WHERE id = ?", categoryId, productId);
                    emit(ProductCatalogChangeType.PRODUCT_UPDATED);
                });
                synchronization.synchronize(productId, 4L);
                change("UPDATE products SET status = 'DELETED' WHERE id = ?", ProductCatalogChangeType.DELETED);
                synchronization.synchronize(productId, 5L);
                synchronization.synchronize(productId, 5L);
                client.indices().refresh(r -> r.index(index));
                var query = new CrossFieldsProductQueryBuilder().buildQuery(new com.project.young.productservice.application.dto.condition.ProductSearchCriteria(
                        null, null, List.of(), null, null, com.project.young.productservice.application.dto.query.PublicProductSort.NEWEST, 0, 1));
                assertThat(client.count(c -> c.index(index).query(query)).count()).isZero();
                assertThat(client.get(g -> g.index(index).id(productId.toString()), OpenSearchProductDocument.class).source().catalogRevision()).isEqualTo(5);
            } finally {
                if (client.indices().exists(r -> r.index(index)).value()) { client.indices().delete(r -> r.index(index)); }
                versions.deleteById(version.id());
            }
        }
    }

    private void change(String sql, ProductCatalogChangeType type) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            revisions.verifyVersion(productId, revisions.current(productId));
            jdbc.update(sql, productId);
            emit(type);
        });
    }

    private void emit(ProductCatalogChangeType type) {
        outbox.enqueue(new ProductCatalogChangedEvent(UUID.randomUUID(), productId, null, type, Instant.now(), type == ProductCatalogChangeType.PRODUCT_CREATED ? 0L : revisions.current(productId)));
    }

    @Configuration
    @Import({ProductDataAccessConfig.class, ProductRepositoryImpl.class, ProductAggregateMapper.class, ProductDataAccessMapper.class, ProductCatalogOutboxAdapter.class, ProductSearchSynchronizationReader.class,
            ProductSearchSynchronizationTargetReader.class, OpenSearchProductDocumentMapper.class, ProductSearchCategoryNameReader.class})
    static class Config { }
}
