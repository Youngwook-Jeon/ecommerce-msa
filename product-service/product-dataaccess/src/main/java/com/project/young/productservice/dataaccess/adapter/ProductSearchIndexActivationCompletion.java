package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;
import com.project.young.productservice.dataaccess.entity.ProductSearchIndexActivationJobEntity;
import com.project.young.productservice.dataaccess.entity.ProductSearchIndexVersionEntity;
import com.project.young.productservice.dataaccess.mapper.ProductSearchIndexMetadataMapper;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexVersionJpaRepository;

import java.time.Instant;
import java.util.Objects;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_STATE_UNCONFIRMED;

/** Both callers own a short transaction, the management lock and the activation job row lock. */
final class ProductSearchIndexActivationCompletion {
    private ProductSearchIndexActivationCompletion() { }

    static boolean metadataMatches(ProductSearchIndexActivationJobEntity job, ProductSearchIndexVersionJpaRepository versions) {
        var target = versions.findById(job.getIndexVersionId()).orElseThrow();
        var active = versions.findByStatus(ProductSearchIndexStatus.ACTIVE).map(ProductSearchIndexVersionEntity::getId).orElse(null);
        return target.getStatus() == ProductSearchIndexStatus.ACTIVATING && Objects.equals(active, job.getExpectedActiveVersionId());
    }

    static void complete(ProductSearchIndexActivationJobEntity entity, ProductSearchIndexVersionJpaRepository versions,
                         ProductSearchIndexMetadataMapper mapper, Instant now) {
        if (!metadataMatches(entity, versions)) {
            throw new ProductSearchIndexManagementException(INDEX_STATE_UNCONFIRMED, "Activation metadata changed before confirmation");
        }
        var job = mapper.toDomain(entity).confirmActivation(entity.getIndexVersionId(), now);
        if (entity.getExpectedActiveVersionId() != null) {
            var previous = versions.findById(entity.getExpectedActiveVersionId()).orElseThrow();
            var inactive = mapper.toDomain(previous).deactivate(now);
            previous.setStatus(inactive.status());
            previous.setUpdatedAt(now);
            versions.flush();
        }
        var target = versions.findById(entity.getIndexVersionId()).orElseThrow();
        var active = mapper.toDomain(target).confirmActive(now);
        target.setStatus(active.status());
        target.setUpdatedAt(now);
        target.setActivatedAt(active.activatedAt());
        entity.setStatus(job.status());
        entity.setUpdatedAt(job.updatedAt());
        entity.setFinishedAt(job.finishedAt());
        entity.setFailureCode(null);
        entity.setFailureMessage(null);
        entity.setOwnerToken(null);
        entity.setLeaseUntil(null);
    }
}
