package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.application.exception.ProductSearchSynchronizationDeferredException;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;
import com.project.young.productservice.dataaccess.repository.ProductSearchIndexVersionJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class ProductSearchSynchronizationTargetReader {
    public record Target(UUID id, String indexName) {
    }

    private final ProductSearchIndexVersionJpaRepository versions;

    public ProductSearchSynchronizationTargetReader(ProductSearchIndexVersionJpaRepository versions) {
        this.versions = versions;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Target read() {
        if (versions.existsByStatus(ProductSearchIndexStatus.ACTIVATING)) {
            throw new ProductSearchSynchronizationDeferredException("Index activation is unresolved");
        }
        var active = versions.findByStatus(ProductSearchIndexStatus.ACTIVE)
                .orElseThrow(() -> new ProductSearchSynchronizationDeferredException("No active search index"));
        return new Target(active.getId(), active.getIndexName());
    }
}
