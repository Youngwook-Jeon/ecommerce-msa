package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.dataaccess.catalog.ProductCatalogRevisionStore;

import com.project.young.productservice.dataaccess.mapper.OpenSearchProductDocumentMapper;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
public class ProductSearchIndexVerificationReader {
    private final JdbcTemplate jdbc;
    private final ProductJpaRepository products;
    private final OpenSearchProductDocumentMapper mapper;
    private final ProductCatalogRevisionStore revisions;

    public ProductSearchIndexVerificationReader(JdbcTemplate jdbc, ProductJpaRepository products, OpenSearchProductDocumentMapper mapper, ProductCatalogRevisionStore revisions) {
        this.jdbc = jdbc;
        this.products = products;
        this.mapper = mapper;
        this.revisions = revisions;
    }

    public record Snapshot(long visibleCount, BigDecimal min, BigDecimal max, OpenSearchProductDocument sample) {
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public Snapshot read() {
        var stats = jdbc.queryForObject("""
                SELECT count(*), min(p.base_price), max(p.base_price) FROM products p
                JOIN categories c ON c.id = p.category_id WHERE p.status = 'ACTIVE' AND c.status = 'ACTIVE'
                """, (rs, row) -> new Snapshot(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3), null));
        var sample = products.findIndexVerificationSample(ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE,
                PageRequest.of(0, 1));
        return new Snapshot(stats.visibleCount(), stats.min(), stats.max(), sample.isEmpty() ? null : mapper.toDocument(sample.getFirst(), revisions.read(List.of(sample.getFirst().getId())).get(sample.getFirst().getId())));
    }
}
