package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.project.young.productservice.web.dto.AdminProductSearchIndexActivationJobResponse;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;
import com.project.young.productservice.web.dto.AdminProductSearchIndexBuildJobResponse;
import com.project.young.productservice.web.dto.AdminProductSearchIndexVersionResponse;
import org.springframework.stereotype.Component;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexReconciliationAttempt;
import com.project.young.productservice.web.dto.AdminProductSearchIndexReconciliationResponse;

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

    public AdminProductSearchIndexActivationJobResponse toActivationResponse(ProductSearchIndexActivationJob job, ProductSearchIndexReconciliationAttempt latest) {
        var failure = job.failureCode() == null ? null : new AdminProductSearchIndexActivationJobResponse.Failure(job.failureCode(), job.failureMessage());
        return new AdminProductSearchIndexActivationJobResponse(job.id(), job.indexVersionId(), job.expectedActiveVersionId(),
                job.aliasName(), job.status(), job.requestedBy(), job.createdAt(), job.updatedAt(), job.startedAt(),
                job.switchStartedAt(), job.finishedAt(), failure, latest == null ? null : toReconciliationResponse(latest));
    }

    public AdminProductSearchIndexReconciliationResponse toReconciliationResponse(ProductSearchIndexReconciliationAttempt attempt) {
        var failure = attempt.failureCode() == null ? null : new AdminProductSearchIndexReconciliationResponse.Failure(attempt.failureCode(), attempt.failureMessage());
        return new AdminProductSearchIndexReconciliationResponse(attempt.id(), attempt.jobId(), attempt.requestedBy() == null ? "AUTOMATIC" : "MANUAL",
                attempt.requestedBy(), attempt.status(), attempt.createdAt(), attempt.finishedAt(), attempt.observation(),
                attempt.observedIndices(), attempt.aliasPlain(), failure);
    }
}
