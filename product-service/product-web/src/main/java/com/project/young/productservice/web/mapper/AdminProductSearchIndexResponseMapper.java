package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import com.project.young.productservice.web.dto.AdminProductSearchIndexBuildJobResponse;
import com.project.young.productservice.web.dto.AdminProductSearchIndexVersionResponse;
import org.springframework.stereotype.Component;

@Component
public class AdminProductSearchIndexResponseMapper {

    public AdminProductSearchIndexVersionResponse toVersionResponse(ProductSearchIndexVersion version) {
        return new AdminProductSearchIndexVersionResponse(version.id(), version.versionNumber(), version.indexName(),
                version.mappingVersion(), version.mappingHash(), version.status(), version.requestedBy(),
                version.createdAt(), version.updatedAt(), version.activatedAt());
    }

    public AdminProductSearchIndexBuildJobResponse toJobResponse(ProductSearchIndexBuildJob job) {
        var failure = job.failureCode() == null ? null : new AdminProductSearchIndexBuildJobResponse.Failure(
                job.failureCode(), job.failureMessage());
        return new AdminProductSearchIndexBuildJobResponse(job.id(), job.indexVersionId(), job.batchSize(), job.status(),
                new AdminProductSearchIndexBuildJobResponse.Progress(job.sourceProductCount(), job.indexedProducts(),
                        job.completedBatches()), job.requestedBy(), job.createdAt(), job.updatedAt(),
                job.startedAt(), job.finishedAt(), failure);
    }
}
