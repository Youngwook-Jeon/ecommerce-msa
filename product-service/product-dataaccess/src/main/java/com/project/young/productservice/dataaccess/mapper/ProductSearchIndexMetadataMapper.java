package com.project.young.productservice.dataaccess.mapper;

import com.project.young.productservice.dataaccess.entity.ProductSearchIndexBuildJobEntity;
import com.project.young.productservice.dataaccess.entity.ProductSearchIndexVersionEntity;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import org.springframework.stereotype.Component;

@Component
public class ProductSearchIndexMetadataMapper {

    public ProductSearchIndexVersion toDomain(ProductSearchIndexVersionEntity entity) {
        return new ProductSearchIndexVersion(entity.getId(), entity.getVersionNumber(), entity.getIndexName(),
                entity.getMappingVersion(), entity.getMappingHash(), entity.getStatus(), entity.getRequestedBy(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getActivatedAt());
    }

    public ProductSearchIndexVersionEntity toEntity(ProductSearchIndexVersion version, long lockVersion) {
        return ProductSearchIndexVersionEntity.builder()
                .id(version.id())
                .versionNumber(version.versionNumber())
                .indexName(version.indexName())
                .mappingVersion(version.mappingVersion())
                .mappingHash(version.mappingHash())
                .status(version.status())
                .requestedBy(version.requestedBy())
                .createdAt(version.createdAt())
                .updatedAt(version.updatedAt())
                .activatedAt(version.activatedAt())
                .lockVersion(lockVersion).build();
    }

    public ProductSearchIndexBuildJob toDomain(ProductSearchIndexBuildJobEntity entity) {
        return new ProductSearchIndexBuildJob(entity.getId(), entity.getIndexVersionId(), entity.getRequestedBy(),
                entity.getIdempotencyKey(), entity.getBatchSize(), entity.getStatus(), entity.getSourceProductCount(),
                entity.getIndexedProducts(), entity.getCompletedBatches(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getStartedAt(), entity.getFinishedAt(), entity.getFailureCode(), entity.getFailureMessage());
    }

    public ProductSearchIndexBuildJobEntity toEntity(ProductSearchIndexBuildJob job, long lockVersion) {
        return ProductSearchIndexBuildJobEntity.builder()
                .id(job.id())
                .indexVersionId(job.indexVersionId())
                .requestedBy(job.requestedBy())
                .idempotencyKey(job.idempotencyKey())
                .batchSize(job.batchSize())
                .status(job.status())
                .sourceProductCount(job.sourceProductCount())
                .indexedProducts(job.indexedProducts())
                .completedBatches(job.completedBatches())
                .createdAt(job.createdAt())
                .updatedAt(job.updatedAt())
                .startedAt(job.startedAt())
                .finishedAt(job.finishedAt())
                .failureCode(job.failureCode())
                .failureMessage(job.failureMessage())
                .lockVersion(lockVersion).build();
    }
}
