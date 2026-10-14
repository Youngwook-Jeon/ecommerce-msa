package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasState;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexReconciliationAttempt;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface ProductSearchIndexReconciliationPort {
    record Work(UUID attemptId, ProductSearchIndexActivationJob job, long jobLockVersion, String targetIndex,
                String previousIndex) {
    }

    void recoverExpired();

    Optional<Work> claimNext(Duration lease, Duration retryDelay);

    Work claim(UUID jobId, String requestedBy, Duration lease);

    ProductSearchIndexReconciliationAttempt finish(Work work, ProductSearchIndexAliasState observed);

    Optional<ProductSearchIndexReconciliationAttempt> latest(UUID jobId);
}
