package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.exception.ProductSearchIndexOwnershipLostException;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildWorkPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildStatus;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;
import com.project.young.productservice.dataaccess.entity.ProductSearchIndexBuildJobEntity;
import com.project.young.productservice.dataaccess.mapper.ProductSearchIndexMetadataMapper;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexBuildJobJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexVersionJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class ProductSearchIndexBuildWorkAdapter implements ProductSearchIndexBuildWorkPort {
    private final ProductSearchIndexBuildJobJpaRepository jobs;
    private final ProductSearchIndexVersionJpaRepository versions;
    private final ProductJpaRepository products;
    private final ProductSearchIndexMetadataMapper mapper;
    private final JdbcTemplate jdbc;

    public ProductSearchIndexBuildWorkAdapter(ProductSearchIndexBuildJobJpaRepository jobs,
            ProductSearchIndexVersionJpaRepository versions, ProductJpaRepository products,
            ProductSearchIndexMetadataMapper mapper, JdbcTemplate jdbc) {
        this.jobs = jobs;
        this.versions = versions;
        this.products = products;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverExpired() {
        var expired = jdbc.query("""
                SELECT id FROM product_search_index_build_jobs
                WHERE status IN ('RUNNING','VERIFYING') AND (lease_until IS NULL OR lease_until <= clock_timestamp())
                FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID id : expired) {
            var entity = jobs.findById(id).orElseThrow();
            var job = mapper.toDomain(entity).interrupt("실행 소유권이 만료되어 작업을 중단했습니다.", now());
            terminate(entity, job);
            failVersion(entity.getIndexVersionId(), job.updatedAt());
            log.warn("Expired index build interrupted: jobId={}, indexVersionId={}", id, entity.getIndexVersionId());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Work> claim(Duration lease) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", (rs, row) -> true, ProductSearchIndexManagementLock.KEY);
        if (versions.existsByStatus(ProductSearchIndexStatus.ACTIVATING)) {
            return Optional.empty();
        }
        var ids = jdbc.query("""
                SELECT id FROM product_search_index_build_jobs WHERE status = 'QUEUED'
                ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getObject(1, UUID.class));
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        var entity = jobs.findById(ids.getFirst()).orElseThrow();
        var versionEntity = versions.findById(entity.getIndexVersionId()).orElseThrow();
        long count = products.count();
        Instant now = now();
        var job = mapper.toDomain(entity).start(count, now);
        var version = mapper.toDomain(versionEntity).beginBuild(now);
        apply(entity, job);
        entity.setOwnerToken(jdbc.queryForObject("SELECT uuidv7()", UUID.class));
        entity.setLeaseUntil(now.plus(lease));
        versionEntity.setStatus(version.status());
        versionEntity.setUpdatedAt(now);
        log.info("Index build claimed: jobId={}, indexVersionId={}, sourceCount={}", job.id(), version.id(), count);
        return Optional.of(new Work(job, version, entity.getOwnerToken()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void renew(Work work, Duration lease) {
        var entity = owned(work);
        entity.setLeaseUntil(now().plus(lease));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBatch(Work work, long products) {
        var entity = owned(work);
        var job = mapper.toDomain(entity).recordCompletedBatch(products, now());
        apply(entity, job);
        log.info("Index build progress saved: jobId={}, indexed={}, batches={}", job.id(), job.indexedProducts(), job.completedBatches());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginVerification(Work work) {
        var entity = owned(work);
        apply(entity, mapper.toDomain(entity).beginVerification(now()));
        log.info("Index build verification started: jobId={}", work.job().id());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Work work, long indexCount) {
        var entity = owned(work);
        var job = mapper.toDomain(entity).succeed(products.count(), indexCount, now());
        var versionEntity = versions.findById(entity.getIndexVersionId()).orElseThrow();
        var version = mapper.toDomain(versionEntity).markReady(job.updatedAt());
        terminate(entity, job);
        versionEntity.setStatus(version.status());
        versionEntity.setUpdatedAt(version.updatedAt());
        log.info("Index build ready: jobId={}, indexVersionId={}, indexed={}", job.id(), version.id(), job.indexedProducts());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Work work) {
        var entity = owned(work);
        var job = mapper.toDomain(entity).fail("INDEX_BUILD_FAILED", "검색 인덱스 생성·적재·검증에 실패했습니다.", now());
        terminate(entity, job);
        failVersion(entity.getIndexVersionId(), job.updatedAt());
        log.warn("Index build failed: jobId={}, indexVersionId={}", job.id(), entity.getIndexVersionId());
    }

    private ProductSearchIndexBuildJobEntity owned(Work work) {
        var entity = jobs.findLockedById(work.job().id()).orElseThrow(ProductSearchIndexOwnershipLostException::new);
        if (!work.ownerToken().equals(entity.getOwnerToken()) || entity.getLeaseUntil() == null
                || !entity.getLeaseUntil().isAfter(now())
                || (entity.getStatus() != ProductSearchIndexBuildStatus.RUNNING && entity.getStatus() != ProductSearchIndexBuildStatus.VERIFYING)) {
            throw new ProductSearchIndexOwnershipLostException();
        }
        return entity;
    }

    private void failVersion(UUID id, Instant now) {
        var entity = versions.findById(id).orElseThrow();
        var version = mapper.toDomain(entity).failBuild(now);
        entity.setStatus(version.status());
        entity.setUpdatedAt(version.updatedAt());
    }

    private static void terminate(ProductSearchIndexBuildJobEntity entity, ProductSearchIndexBuildJob job) {
        apply(entity, job);
        entity.setOwnerToken(null);
        entity.setLeaseUntil(null);
    }

    private static void apply(ProductSearchIndexBuildJobEntity entity, ProductSearchIndexBuildJob job) {
        entity.setStatus(job.status());
        entity.setSourceProductCount(job.sourceProductCount());
        entity.setIndexedProducts(job.indexedProducts());
        entity.setCompletedBatches(job.completedBatches());
        entity.setUpdatedAt(job.updatedAt());
        entity.setStartedAt(job.startedAt());
        entity.setFinishedAt(job.finishedAt());
        entity.setFailureCode(job.failureCode());
        entity.setFailureMessage(job.failureMessage());
    }

    private Instant now() {
        return jdbc.queryForObject("SELECT clock_timestamp()", (rs, row) -> rs.getTimestamp(1).toInstant());
    }
}
