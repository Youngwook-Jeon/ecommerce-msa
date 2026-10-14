package com.project.young.productservice.dataaccess.mapper;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexReconciliationAttempt;
import com.project.young.productservice.dataaccess.entity.ProductSearchIndexReconciliationAttemptEntity;
import org.springframework.stereotype.Component;

@Component
public class ProductSearchIndexReconciliationMapper {
    public ProductSearchIndexReconciliationAttempt toModel(ProductSearchIndexReconciliationAttemptEntity entity) {
        return new ProductSearchIndexReconciliationAttempt(entity.getId(), entity.getJobId(), entity.getRequestedBy(),
                entity.getStatus(), entity.getCreatedAt(), entity.getLeaseUntil(), entity.getFinishedAt(), entity.getObservation(),
                entity.getObservedIndices(), entity.getAliasPlain(), entity.getFailureCode(), entity.getFailureMessage());
    }
}
