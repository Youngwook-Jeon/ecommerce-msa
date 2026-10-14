package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexBuildCommand;
import com.project.young.productservice.application.dto.result.ProductSearchIndexVersionPageResult;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.port.output.ProductSearchIndexMetadataPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildStatus;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import com.project.young.productservice.dataaccess.mapper.ProductSearchIndexMetadataMapper;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexBuildJobJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexVersionJpaRepository;
import lombok.extern.slf4j.Slf4j;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexMappingProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.IDEMPOTENCY_KEY_CONFLICT;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_ACTIVATION_IN_PROGRESS;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_BUILD_IN_PROGRESS;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_MANAGEMENT_UNAVAILABLE;

@Slf4j
@Component
public class ProductSearchIndexMetadataAdapter implements ProductSearchIndexMetadataPort {
    // Build and activation registration use the same transaction lock before checking management state.
    private static final int MAPPING_VERSION = ProductSearchIndexMappingProperties.CURRENT_VERSION;
    private final ProductSearchIndexVersionJpaRepository versions;
    private final ProductSearchIndexBuildJobJpaRepository jobs;
    private final ProductSearchIndexMetadataMapper mapper;
    private final JdbcTemplate jdbc;
    private final ProductSearchIndexMappingProperties mappingProperties;

    public ProductSearchIndexMetadataAdapter(ProductSearchIndexVersionJpaRepository versions,
            ProductSearchIndexBuildJobJpaRepository jobs, ProductSearchIndexMetadataMapper mapper, JdbcTemplate jdbc,
            ProductSearchIndexMappingProperties mappingProperties) {
        this.versions = versions;
        this.jobs = jobs;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.mappingProperties = mappingProperties;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductSearchIndexBuildJob register(RegisterProductSearchIndexBuildCommand command) {
        try {
            acquireManagementLock();
            var existing = jobs.findByRequestedByAndIdempotencyKey(command.requestedBy(), command.idempotencyKey());
            if (existing.isPresent()) {
                if (existing.get().getBatchSize() != command.batchSize()) {
                    throw new ProductSearchIndexManagementException(IDEMPOTENCY_KEY_CONFLICT, "Idempotency key was used for a different request");
                }
                return mapper.toDomain(existing.get());
            }
            if (versions.existsByStatus(ProductSearchIndexStatus.ACTIVATING)) {
                throw new ProductSearchIndexManagementException(INDEX_ACTIVATION_IN_PROGRESS, "Index activation is in progress");
            }
            if (jobs.existsByStatusIn(List.of(ProductSearchIndexBuildStatus.QUEUED,
                    ProductSearchIndexBuildStatus.RUNNING, ProductSearchIndexBuildStatus.VERIFYING))) {
                throw new ProductSearchIndexManagementException(INDEX_BUILD_IN_PROGRESS, "Another index build is in progress");
            }
            long number = nextVersionNumber();
            UUID versionId = newMetadataId();
            UUID jobId = newMetadataId();
            Instant now = registrationTime();
            var version = ProductSearchIndexVersion.register(versionId, number, MAPPING_VERSION, mappingHash(), command.requestedBy(), now);
            var job = ProductSearchIndexBuildJob.queue(jobId, versionId, command.requestedBy(), command.idempotencyKey(), command.batchSize(), now);
            versions.saveAndFlush(mapper.toEntity(version, 0));
            jobs.saveAndFlush(mapper.toEntity(job, 0));
            log.info("Search index build registered: jobId={}, indexVersionId={}, versionNumber={}", jobId, versionId, number);
            return job;
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductSearchIndexBuildJob> findJob(UUID jobId) {
        try {
            return jobs.findById(jobId).map(mapper::toDomain);
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProductSearchIndexVersionPageResult listVersions(int page, int size) {
        try {
            var result = versions.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
            return new ProductSearchIndexVersionPageResult(result.getContent().stream().map(mapper::toDomain).toList(),
                    page, size, result.getTotalElements());
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw unavailable(exception);
        }
    }

    private void acquireManagementLock() {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", (rs, row) -> true, ProductSearchIndexManagementLock.KEY);
    }

    private long nextVersionNumber() {
        return jdbc.queryForObject("SELECT nextval('product_search_index_version_number_seq')", Long.class);
    }

    private UUID newMetadataId() {
        return jdbc.queryForObject("SELECT uuidv7()", UUID.class);
    }

    private Instant registrationTime() {
        return jdbc.queryForObject("SELECT clock_timestamp()", (rs, row) -> rs.getTimestamp(1).toInstant());
    }

    private String mappingHash() {
        try (var input = mappingProperties.resource().getInputStream()) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw unavailable(exception);
        }
    }

    private static ProductSearchIndexManagementException unavailable(Exception exception) {
        log.warn("Index metadata operation failed: exceptionType={}", exception.getClass().getSimpleName());
        return new ProductSearchIndexManagementException(INDEX_MANAGEMENT_UNAVAILABLE, "Index management is unavailable", exception);
    }
}
