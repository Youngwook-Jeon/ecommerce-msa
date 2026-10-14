package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexActivationCommand;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.exception.ProductSearchIndexOwnershipLostException;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationMetadataPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationStatus;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildStatus;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;
import com.project.young.productservice.dataaccess.entity.ProductSearchIndexActivationJobEntity;
import com.project.young.productservice.dataaccess.entity.ProductSearchIndexVersionEntity;
import com.project.young.productservice.dataaccess.mapper.ProductSearchIndexMetadataMapper;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexActivationJobJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexBuildJobJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexVersionJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.IDEMPOTENCY_KEY_CONFLICT;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_ACTIVATION_IN_PROGRESS;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_BUILD_IN_PROGRESS;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_NOT_READY;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.ACTIVE_INDEX_CHANGED;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_STATE_UNCONFIRMED;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_VERSION_NOT_FOUND;

@Slf4j
@Component
public class ProductSearchIndexActivationAdapter implements ProductSearchIndexActivationMetadataPort, ProductSearchIndexActivationWorkPort {
    private static final List<ProductSearchIndexActivationStatus> UNFINISHED = List.of(ProductSearchIndexActivationStatus.QUEUED,
            ProductSearchIndexActivationStatus.RUNNING, ProductSearchIndexActivationStatus.SWITCHING, ProductSearchIndexActivationStatus.UNCONFIRMED);
    private final ProductSearchIndexActivationJobJpaRepository jobs;
    private final ProductSearchIndexVersionJpaRepository versions;
    private final ProductSearchIndexBuildJobJpaRepository builds;
    private final ProductSearchIndexMetadataMapper mapper;
    private final JdbcTemplate jdbc;
    private final String aliasName;

    public ProductSearchIndexActivationAdapter(ProductSearchIndexActivationJobJpaRepository jobs,
            ProductSearchIndexVersionJpaRepository versions, ProductSearchIndexBuildJobJpaRepository builds,
            ProductSearchIndexMetadataMapper mapper, JdbcTemplate jdbc,
            @Value("${product-service.search.index:products-read}") String aliasName) {
        this.jobs = jobs;
        this.versions = versions;
        this.builds = builds;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.aliasName = aliasName;
    }

    @Override
    @Transactional(readOnly = true)
    public Context context(UUID indexVersionId) {
        var target = target(indexVersionId);
        var active = versions.findByStatus(ProductSearchIndexStatus.ACTIVE).map(mapper::toDomain).orElse(null);
        return new Context(mapper.toDomain(target), active, aliasName);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductSearchIndexActivationJob> findRequest(String requestedBy, String key) {
        return jobs.findByRequestedByAndIdempotencyKey(requestedBy, key).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductSearchIndexActivationJob> findJob(UUID id) {
        return jobs.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductSearchIndexActivationJob register(RegisterProductSearchIndexActivationCommand command) {
        acquireManagementLock();
        var existing = jobs.findByRequestedByAndIdempotencyKey(command.requestedBy(), command.idempotencyKey());
        if (existing.isPresent()) {
            var job = mapper.toDomain(existing.get());
            if (!job.indexVersionId().equals(command.indexVersionId())
                    || !Objects.equals(job.expectedActiveVersionId(), command.expectedActiveVersionId())) {
                throw management(IDEMPOTENCY_KEY_CONFLICT, "Idempotency key was used for a different activation request");
            }
            return job;
        }
        if (jobs.existsByStatusIn(UNFINISHED) || versions.existsByStatus(ProductSearchIndexStatus.ACTIVATING)) {
            throw management(INDEX_ACTIVATION_IN_PROGRESS, "Another activation is unresolved");
        }
        ensureNoBuild();
        var target = target(command.indexVersionId());
        if (target.getStatus() != ProductSearchIndexStatus.READY) {
            throw management(INDEX_NOT_READY, "Only a READY index can be activated");
        }
        ensureExpectedActive(command.expectedActiveVersionId());
        Instant now = now();
        var job = ProductSearchIndexActivationJob.queue(jdbc.queryForObject("SELECT uuidv7()", UUID.class), target.getId(),
                command.expectedActiveVersionId(), aliasName, command.requestedBy(), command.idempotencyKey(), now);
        var activating = mapper.toDomain(target).beginActivation(now);
        target.setStatus(activating.status());
        target.setUpdatedAt(now);
        jobs.saveAndFlush(mapper.toEntity(job, 0));
        log.info("Index activation registered: jobId={}, indexVersionId={}, alias={}, expectedActiveVersionId={}",
                job.id(), job.indexVersionId(), job.aliasName(), job.expectedActiveVersionId());
        return job;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverExpired() {
        acquireManagementLock();
        var ids = jdbc.query("""
                SELECT id FROM product_search_index_activation_jobs
                WHERE status IN ('RUNNING','SWITCHING') AND (lease_until IS NULL OR lease_until <= clock_timestamp())
                FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID id : ids) {
            var entity = jobs.findById(id).orElseThrow();
            resolveFailure(entity, "ACTIVATION_WORKER_INTERRUPTED", "활성화 실행 소유권이 만료되었습니다.");
            log.warn("Expired activation stopped: jobId={}, indexVersionId={}, status={}", id, entity.getIndexVersionId(), entity.getStatus());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Work> claim(Duration lease) {
        acquireManagementLock();
        if (buildInProgress()) {
            return Optional.empty();
        }
        var ids = jdbc.query("""
                SELECT id FROM product_search_index_activation_jobs WHERE status = 'QUEUED'
                ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getObject(1, UUID.class));
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        var entity = jobs.findById(ids.getFirst()).orElseThrow();
        var target = target(entity.getIndexVersionId());
        Instant now = now();
        var job = mapper.toDomain(entity).start(now);
        apply(entity, job);
        entity.setOwnerToken(jdbc.queryForObject("SELECT uuidv7()", UUID.class));
        entity.setLeaseUntil(now.plus(lease));
        String previous = job.expectedActiveVersionId() == null ? null : target(job.expectedActiveVersionId()).getIndexName();
        log.info("Index activation claimed: jobId={}, indexVersionId={}, alias={}", job.id(), job.indexVersionId(), job.aliasName());
        return Optional.of(new Work(job, mapper.toDomain(target), previous, entity.getOwnerToken()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void renew(Work work, Duration lease) {
        owned(work).setLeaseUntil(now().plus(lease));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginSwitch(Work work) {
        acquireManagementLock();
        var entity = owned(work);
        ensureNoBuild();
        ensureExpectedActive(entity.getExpectedActiveVersionId());
        if (target(entity.getIndexVersionId()).getStatus() != ProductSearchIndexStatus.ACTIVATING) {
            throw management(INDEX_STATE_UNCONFIRMED, "Target is no longer ACTIVATING");
        }
        apply(entity, mapper.toDomain(entity).beginSwitch(now()));
        log.info("Alias switch intent saved: jobId={}, indexVersionId={}, alias={}", entity.getId(), entity.getIndexVersionId(), entity.getAliasName());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Work work) {
        acquireManagementLock();
        var entity = owned(work);
        ensureExpectedActive(entity.getExpectedActiveVersionId());
        ProductSearchIndexActivationCompletion.complete(entity, versions, mapper, now());
        log.info("Index activation confirmed: jobId={}, indexVersionId={}, alias={}, previousVersionId={}",
                entity.getId(), entity.getIndexVersionId(), entity.getAliasName(), entity.getExpectedActiveVersionId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failOrUnconfirm(Work work) {
        acquireManagementLock();
        resolveFailure(owned(work), "INDEX_ACTIVATION_FAILED", "검색 인덱스 활성화 결과를 확인해 주세요.");
    }

    private void resolveFailure(ProductSearchIndexActivationJobEntity entity, String code, String message) {
        var job = mapper.toDomain(entity);
        Instant now = now();
        if (job.status() == ProductSearchIndexActivationStatus.RUNNING) {
            job = job.failBeforeSwitch(code, message, now);
            var target = target(entity.getIndexVersionId());
            var ready = mapper.toDomain(target).cancelActivationBeforeSwitch(now);
            target.setStatus(ready.status());
            target.setUpdatedAt(now);
        } else {
            job = job.markUnconfirmed(code, message, now);
        }
        apply(entity, job);
        clearOwner(entity);
        log.warn("Activation failure recorded: jobId={}, indexVersionId={}, alias={}, status={}", job.id(), job.indexVersionId(), job.aliasName(), job.status());
    }

    private ProductSearchIndexActivationJobEntity owned(Work work) {
        var entity = jobs.findLockedById(work.job().id()).orElseThrow(ProductSearchIndexOwnershipLostException::new);
        if (!work.ownerToken().equals(entity.getOwnerToken()) || entity.getLeaseUntil() == null
                || !entity.getLeaseUntil().isAfter(now())
                || (entity.getStatus() != ProductSearchIndexActivationStatus.RUNNING && entity.getStatus() != ProductSearchIndexActivationStatus.SWITCHING)) {
            throw new ProductSearchIndexOwnershipLostException();
        }
        return entity;
    }

    private ProductSearchIndexVersionEntity target(UUID id) {
        return versions.findById(id).orElseThrow(() -> management(INDEX_VERSION_NOT_FOUND, "Index version was not found"));
    }

    private void ensureExpectedActive(UUID expected) {
        var actual = versions.findByStatus(ProductSearchIndexStatus.ACTIVE).map(ProductSearchIndexVersionEntity::getId).orElse(null);
        if (!Objects.equals(expected, actual)) {
            throw management(ACTIVE_INDEX_CHANGED, "Active index version changed");
        }
    }

    private boolean buildInProgress() {
        return builds.existsByStatusIn(List.of(ProductSearchIndexBuildStatus.QUEUED, ProductSearchIndexBuildStatus.RUNNING, ProductSearchIndexBuildStatus.VERIFYING));
    }

    private void ensureNoBuild() {
        if (buildInProgress()) {
            throw management(INDEX_BUILD_IN_PROGRESS, "An index build is in progress");
        }
    }

    private void acquireManagementLock() {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", (rs, row) -> true, ProductSearchIndexManagementLock.KEY);
    }

    private Instant now() {
        return jdbc.queryForObject("SELECT clock_timestamp()", (rs, row) -> rs.getTimestamp(1).toInstant());
    }

    private static void clearOwner(ProductSearchIndexActivationJobEntity entity) {
        entity.setOwnerToken(null);
        entity.setLeaseUntil(null);
    }

    private static void apply(ProductSearchIndexActivationJobEntity entity, ProductSearchIndexActivationJob job) {
        entity.setStatus(job.status());
        entity.setUpdatedAt(job.updatedAt());
        entity.setStartedAt(job.startedAt());
        entity.setSwitchStartedAt(job.switchStartedAt());
        entity.setFinishedAt(job.finishedAt());
        entity.setFailureCode(job.failureCode());
        entity.setFailureMessage(job.failureMessage());
    }

    private static ProductSearchIndexManagementException management(ProductSearchIndexManagementException.Code code, String message) {
        return new ProductSearchIndexManagementException(code, message);
    }
}
