package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.dataaccess.mapper.OpenSearchProductDocumentMapper;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ProductSearchIndexBatchReader {

    private final ProductJpaRepository repository;
    private final OpenSearchProductDocumentMapper mapper;

    public ProductSearchIndexBatchReader(ProductJpaRepository repository, OpenSearchProductDocumentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<OpenSearchProductDocument> read(UUID afterId, int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        var page = PageRequest.of(0, batchSize);
        var products = afterId == null ? repository.findFirstSearchIndexBatch(page)
                : repository.findNextSearchIndexBatch(afterId, page);
        return products.stream().map(mapper::toDocument).toList();
    }
}
