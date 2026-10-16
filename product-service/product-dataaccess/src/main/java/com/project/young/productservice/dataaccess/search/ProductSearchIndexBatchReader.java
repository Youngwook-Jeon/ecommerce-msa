package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.dataaccess.catalog.ProductCatalogRevisionStore;

import com.project.young.productservice.dataaccess.mapper.OpenSearchProductDocumentMapper;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ProductSearchIndexBatchReader {

    private final ProductJpaRepository repository;
    private final OpenSearchProductDocumentMapper mapper;
    private final ProductCatalogRevisionStore revisions;

    public ProductSearchIndexBatchReader(ProductJpaRepository repository, OpenSearchProductDocumentMapper mapper, ProductCatalogRevisionStore revisions) {
        this.repository = repository;
        this.mapper = mapper;
        this.revisions = revisions;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public List<OpenSearchProductDocument> read(UUID afterId, int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        var page = PageRequest.of(0, batchSize);
        var products = afterId == null ? repository.findFirstSearchIndexBatch(page)
                : repository.findNextSearchIndexBatch(afterId, page);
        var versions = revisions.read(products.stream().map(p -> p.getId()).toList());
        return products.stream().map(p -> mapper.toDocument(p, requireRevision(versions.get(p.getId())))).toList();
    }

    private static long requireRevision(Long revision) {
        if (revision == null || revision < 1) { throw new IllegalStateException("Product search revision is missing"); }
        return revision;
    }
}
