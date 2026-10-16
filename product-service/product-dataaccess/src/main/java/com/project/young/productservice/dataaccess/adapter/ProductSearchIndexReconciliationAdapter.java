package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.exception.ProductSearchIndexOwnershipLostException;
import com.project.young.productservice.application.port.output.ProductSearchIndexReconciliationPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationStatus;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasObservation;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasState;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexReconciliationAttempt;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexReconciliationStatus;
import com.project.young.productservice.dataaccess.entity.ProductSearchIndexReconciliationAttemptEntity;
import com.project.young.productservice.dataaccess.mapper.ProductSearchIndexMetadataMapper;
import com.project.young.productservice.dataaccess.mapper.ProductSearchIndexReconciliationMapper;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexActivationJobJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexVersionJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexReconciliationAttemptJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_ACTIVATION_NOT_FOUND;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_ACTIVATION_NOT_UNCONFIRMED;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_RECONCILIATION_IN_PROGRESS;

@Slf4j
@Component
public class ProductSearchIndexReconciliationAdapter implements ProductSearchIndexReconciliationPort {
    private final ProductSearchIndexActivationJobJpaRepository jobs;
    private final ProductSearchIndexVersionJpaRepository versions;
    private final ProductSearchIndexReconciliationAttemptJpaRepository attempts;
    private final ProductSearchIndexMetadataMapper metadataMapper;
    private final ProductSearchIndexReconciliationMapper mapper;
    private final JdbcTemplate jdbc;

    public ProductSearchIndexReconciliationAdapter(ProductSearchIndexActivationJobJpaRepository jobs,
                                                   ProductSearchIndexVersionJpaRepository versions, ProductSearchIndexReconciliationAttemptJpaRepository attempts,
                                                   ProductSearchIndexMetadataMapper metadataMapper, ProductSearchIndexReconciliationMapper mapper, JdbcTemplate jdbc) {
        this.jobs = jobs;
        this.versions = versions;
        this.attempts = attempts;
        this.metadataMapper = metadataMapper;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverExpired() {
        lock();
        var ids = jdbc.query("""
                SELECT id FROM product_search_index_reconciliation_attempts
                WHERE finished_at IS NULL AND lease_until <= clock_timestamp() FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID id : ids) {
            var entity = attempts.findById(id).orElseThrow();
            entity.setStatus(ProductSearchIndexReconciliationStatus.EXPIRED);
            entity.setFinishedAt(now());
            entity.setFailureCode("RECONCILIATION_LEASE_EXPIRED");
            entity.setFailureMessage("The reconciliation attempt execution lease expired.");
            log.warn("Reconciliation attempt expired: jobId={}, attemptId={}", entity.getJobId(), id);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Work> claimNext(Duration lease, Duration retryDelay) {
        lock();
        var ids = jdbc.query("""
                SELECT j.id FROM product_search_index_activation_jobs j
                WHERE j.status = 'UNCONFIRMED' AND j.owner_token IS NULL
                  AND NOT EXISTS (SELECT 1 FROM product_search_index_reconciliation_attempts a
                                  WHERE a.job_id = j.id AND (a.finished_at IS NULL
                                      OR a.finished_at > clock_timestamp() - (? * interval '1 millisecond')))
                ORDER BY j.updated_at, j.id LIMIT 1 FOR UPDATE OF j SKIP LOCKED
                """, (rs, row) -> rs.getObject(1, UUID.class), retryDelay.toMillis());
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(claimLocked(ids.getFirst(), null, lease));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Work claim(UUID jobId, String requestedBy, Duration lease) {
        lock();
        return claimLocked(jobId, requestedBy, lease);
    }

    private Work claimLocked(UUID jobId, String requestedBy, Duration lease) {
        var entity = jobs.findLockedById(jobId).orElseThrow(() -> management(INDEX_ACTIVATION_NOT_FOUND, "Index activation job was not found"));
        if (entity.getStatus() != ProductSearchIndexActivationStatus.UNCONFIRMED || entity.getOwnerToken() != null) {
            throw management(INDEX_ACTIVATION_NOT_UNCONFIRMED, "Only an unowned UNCONFIRMED activation can be rechecked");
        }
        if (attempts.existsByJobIdAndFinishedAtIsNull(jobId)) {
            throw management(INDEX_RECONCILIATION_IN_PROGRESS, "A reconciliation attempt is already running");
        }
        Instant now = now();
        UUID id = jdbc.queryForObject("SELECT uuidv7()", UUID.class);
        attempts.saveAndFlush(ProductSearchIndexReconciliationAttemptEntity.builder().id(id).jobId(jobId).requestedBy(requestedBy)
                .status(ProductSearchIndexReconciliationStatus.RUNNING).createdAt(now).leaseUntil(now.plus(lease)).build());
        String target = versions.findById(entity.getIndexVersionId()).orElseThrow().getIndexName();
        String previous = entity.getExpectedActiveVersionId() == null ? null : versions.findById(entity.getExpectedActiveVersionId()).orElseThrow().getIndexName();
        return new Work(id, metadataMapper.toDomain(entity), entity.getLockVersion(), target, previous);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductSearchIndexReconciliationAttempt finish(Work work, ProductSearchIndexAliasState observed) {
        lock();
        var job = jobs.findLockedById(work.job().id()).orElseThrow(ProductSearchIndexOwnershipLostException::new);
        var attempt = attempts.findLockedById(work.attemptId()).orElseThrow(ProductSearchIndexOwnershipLostException::new);
        if (!attempt.getJobId().equals(job.getId()) || attempt.getStatus() != ProductSearchIndexReconciliationStatus.RUNNING
                || !attempt.getLeaseUntil().isAfter(now())) {
            throw new ProductSearchIndexOwnershipLostException();
        }
        Instant now = now();
        var observation = observed == null ? ProductSearchIndexAliasObservation.LOOKUP_FAILED : observed.classify(work.targetIndex(), work.previousIndex());
        attempt.setObservation(observation);
        if (observed != null) {
            attempt.setObservedIndices(observed.indices().stream().sorted().toList());
            attempt.setAliasPlain(observed.plain());
        }
        boolean unchanged = job.getStatus() == ProductSearchIndexActivationStatus.UNCONFIRMED
                && job.getOwnerToken() == null && job.getLockVersion() == work.jobLockVersion()
                && ProductSearchIndexActivationCompletion.metadataMatches(job, versions)
                && versions.findById(job.getIndexVersionId()).orElseThrow().getIndexName().equals(work.targetIndex())
                && (job.getExpectedActiveVersionId() == null || versions.findById(job.getExpectedActiveVersionId()).orElseThrow().getIndexName().equals(work.previousIndex()));
        if (!unchanged) {
            attempt.setStatus(ProductSearchIndexReconciliationStatus.STALE);
            attempt.setFailureCode("RECONCILIATION_STATE_CHANGED");
            attempt.setFailureMessage("The result was not applied because the index management state changed during verification.");
            log.warn("Reconciliation snapshot became stale: jobId={}, attemptId={}, observation={}", job.getId(), attempt.getId(), observation);
        } else if (observation == ProductSearchIndexAliasObservation.TARGET_CONFIRMED) {
            ProductSearchIndexActivationCompletion.complete(job, versions, metadataMapper, now);
            attempt.setStatus(ProductSearchIndexReconciliationStatus.SUCCEEDED);
            log.info("Activation reconciled: jobId={}, attemptId={}, alias={}, targetIndex={}", job.getId(), attempt.getId(), job.getAliasName(), work.targetIndex());
        } else if (observed == null) {
            attempt.setStatus(ProductSearchIndexReconciliationStatus.FAILED);
            attempt.setFailureCode("ALIAS_LOOKUP_FAILED");
            attempt.setFailureMessage("Failed to read the OpenSearch alias state.");
        } else {
            attempt.setStatus(ProductSearchIndexReconciliationStatus.UNRESOLVED);
            if (observation == ProductSearchIndexAliasObservation.MULTIPLE_INDICES
                    || observation == ProductSearchIndexAliasObservation.UNEXPECTED_INDEX
                    || observation == ProductSearchIndexAliasObservation.ALIAS_OPTIONS_MISMATCH) {
                log.warn("Reconciliation requires operator inspection: jobId={}, attemptId={}, observation={}, indices={}",
                        job.getId(), attempt.getId(), observation, attempt.getObservedIndices());
            } else {
                log.debug("Activation still unconfirmed: jobId={}, attemptId={}, observation={}", job.getId(), attempt.getId(), observation);
            }
        }
        attempt.setFinishedAt(now);
        return mapper.toModel(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductSearchIndexReconciliationAttempt> latest(UUID jobId) {
        return attempts.findFirstByJobIdOrderByCreatedAtDescIdDesc(jobId).map(mapper::toModel);
    }

    private void lock() {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", (rs, row) -> true, ProductSearchIndexManagementLock.KEY);
    }

    private Instant now() {
        return jdbc.queryForObject("SELECT clock_timestamp()", (rs, row) -> rs.getTimestamp(1).toInstant());
    }

    private static ProductSearchIndexManagementException management(ProductSearchIndexManagementException.Code code, String message) {
        return new ProductSearchIndexManagementException(code, message);
    }
}
