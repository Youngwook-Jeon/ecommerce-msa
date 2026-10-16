package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.dataaccess.catalog.ProductCatalogRevisionStore;

import com.project.young.productservice.application.exception.ProductSearchSynchronizationDeferredException;
import com.project.young.productservice.dataaccess.mapper.OpenSearchProductDocumentMapper;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ProductSearchSynchronizationReader {
    private final ProductJpaRepository products;
    private final ProductCatalogRevisionStore revisions;
    private final OpenSearchProductDocumentMapper mapper;

    public ProductSearchSynchronizationReader(ProductJpaRepository products, ProductCatalogRevisionStore revisions, OpenSearchProductDocumentMapper mapper) {
        this.products = products;
        this.revisions = revisions;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public OpenSearchProductDocument read(UUID id, Long eventRevision) {
        var product = products.findSearchDocumentById(id).orElseThrow(() -> new IllegalStateException("Catalog event refers to a missing product"));
        Long revision = revisions.read(List.of(id)).get(id);
        if (revision == null || revision < 1 || (eventRevision != null && revision < eventRevision)) {
            throw new ProductSearchSynchronizationDeferredException("Catalog snapshot has not reached the event revision");
        }
        return mapper.toDocument(product, revision);
    }
}
