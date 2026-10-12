package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.mapper.ProductSearchIndexMetadataMapper;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = ProductSearchIndexMetadataRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductSearchIndexMetadataRepositoryTest {

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

    private final ProductSearchIndexMetadataMapper mapper = new ProductSearchIndexMetadataMapper();
    private final Instant now = Instant.parse("2026-10-12T00:00:00Z");

    @Test
    void database_withoutExplicitIds_generatesUuidV7ForVersionsAndJobs() {
        UUID versionId = jdbcTemplate.queryForObject("""
                INSERT INTO product_search_index_versions
                    (version_number, index_name, mapping_version, mapping_hash, status, requested_by, created_at, updated_at)
                VALUES (1, 'products-v1', 1, ?, 'REGISTERED', 'admin-sub', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, UUID.class, "a".repeat(64));
        UUID jobId = jdbcTemplate.queryForObject("""
                INSERT INTO product_search_index_build_jobs
                    (index_version_id, requested_by, idempotency_key, batch_size, status, created_at, updated_at)
                VALUES (?, 'admin-sub', 'request-1', 500, 'QUEUED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, UUID.class, versionId);
        assertThat(versionId).isNotNull();
        assertThat(jobId).isNotNull();
        assertThat(versionId.version()).isEqualTo(7);
        assertThat(jobId.version()).isEqualTo(7);
        assertThat(jobId).isNotEqualTo(versionId);
    }

    @Test
    void database_metadataTables_doNotDuplicateApplicationCheckRules() {
        Long checks = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE contype = 'c' AND conrelid IN (
                    'product_search_index_versions'::regclass,
                    'product_search_index_build_jobs'::regclass)
                """, Long.class);
        assertThat(checks).isZero();
    }

    @Test
    void database_jobWithMissingIndexVersion_stillRejectsForeignKeyViolation() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO product_search_index_build_jobs
                    (index_version_id, requested_by, idempotency_key, batch_size, status, created_at, updated_at)
                VALUES (?, 'admin-sub', 'request-1', 500, 'QUEUED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void metadata_afterPersistence_roundTripsAndPreservesOptimisticVersion() {
        var version = registered(1);
        var entity = versions.saveAndFlush(mapper.toEntity(version, 0));
        assertThat(mapper.toDomain(versions.findById(version.id()).orElseThrow())).isEqualTo(version);
        var job = queued(version, "request-1");
        jobs.saveAndFlush(mapper.toEntity(job, 0));
        assertThat(mapper.toDomain(jobs.findById(job.id()).orElseThrow())).isEqualTo(job);
        entity.setStatus(version.beginBuild(now).status());
        versions.saveAndFlush(entity);
        assertThat(entity.getLockVersion()).isEqualTo(1);
    }

    @Test
    void database_withTwoUnfinishedJobs_rejectsConcurrentRegistration() {
        var first = versions.saveAndFlush(mapper.toEntity(registered(1), 0));
        var second = versions.saveAndFlush(mapper.toEntity(registered(2), 0));
        jobs.saveAndFlush(mapper.toEntity(queued(mapper.toDomain(first), "request-1"), 0));
        assertThatThrownBy(() -> jobs.saveAndFlush(mapper.toEntity(queued(mapper.toDomain(second), "request-2"), 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_afterTerminalJob_rejectsReusedAdminIdempotencyKey() {
        var first = registered(1);
        var second = registered(2);
        versions.saveAndFlush(mapper.toEntity(first, 0));
        versions.saveAndFlush(mapper.toEntity(second, 0));
        jobs.saveAndFlush(mapper.toEntity(queued(first, "request-1").fail("INVALID_DOCUMENT", "문서 생성 실패", now), 0));
        assertThatThrownBy(() -> jobs.saveAndFlush(mapper.toEntity(queued(second, "request-1"), 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_withTwoActiveVersions_rejectsSecondActiveVersion() {
        var first = registered(1).beginBuild(now).markReady(now).beginActivation(now).confirmActive(now);
        var second = registered(2).beginBuild(now).markReady(now).beginActivation(now).confirmActive(now);
        versions.saveAndFlush(mapper.toEntity(first, 0));
        assertThatThrownBy(() -> versions.saveAndFlush(mapper.toEntity(second, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ProductSearchIndexVersion registered(long number) {
        return ProductSearchIndexVersion.register(UUID.randomUUID(), number, 1, "a".repeat(64), "admin-sub", now);
    }

    private ProductSearchIndexBuildJob queued(ProductSearchIndexVersion version, String key) {
        return ProductSearchIndexBuildJob.queue(UUID.randomUUID(), version.id(), "admin-sub", key, 500, now);
    }

    @Configuration
    @Import(ProductDataAccessConfig.class)
    static class Config {
    }
}
