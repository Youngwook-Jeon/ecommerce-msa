package com.project.young.productservice.dataaccess.catalog;

import com.project.young.productservice.application.exception.ProductVersionConflictException;
import com.project.young.productservice.application.port.output.ProductCatalogVersionPort;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ProductCatalogRevisionStore implements ProductCatalogVersionPort {
    private final JdbcTemplate jdbc;

    public ProductCatalogRevisionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(UUID productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        jdbc.update("INSERT INTO product_catalog_versions(product_id, revision) VALUES (?, 1)", productId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void verifyVersion(UUID productId, long expectedVersion) {
        if (productId == null || expectedVersion < 1) {
            throw new IllegalArgumentException("Product ID and positive expectedVersion are required");
        }
        if (current(productId) != expectedVersion) {
            throw new ProductVersionConflictException(productId, expectedVersion);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long advance(UUID productId, long expectedVersion) {
        if (productId == null || expectedVersion < 1) {
            throw new IllegalArgumentException("Product ID and positive expectedVersion are required");
        }
        List<Long> revisions = jdbc.query("""
                UPDATE product_catalog_versions SET revision = revision + 1
                WHERE product_id = ? AND revision = ? RETURNING revision
                """, (rs, row) -> rs.getLong(1), productId, expectedVersion);
        if (revisions.isEmpty()) {
            throw new ProductVersionConflictException(productId, expectedVersion);
        }
        return revisions.getFirst();
    }

    public long current(UUID productId) {
        Long revision = read(List.of(productId)).get(productId);
        if (revision == null) {
            Boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM products WHERE id = ?)", Boolean.class, productId);
            if (Boolean.TRUE.equals(exists)) {
                throw new IllegalStateException("Product catalog revision is missing: productId=" + productId);
            }
            throw new ProductNotFoundException("Product not found: " + productId);
        }
        if (revision < 1) {
            throw new IllegalStateException("Product catalog revision must be positive: productId=" + productId);
        }
        return revision;
    }

    /** Caller holds the snapshot used to read the product projection. */
    public Map<UUID, Long> read(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        return jdbc.query("SELECT product_id, revision FROM product_catalog_versions WHERE product_id IN (" + placeholders + ")",
                rs -> {
                    var result = new HashMap<UUID, Long>();
                    while (rs.next()) {
                        result.put(rs.getObject(1, UUID.class), rs.getLong(2));
                    }
                    return Map.copyOf(result);
                }, ids.toArray());
    }
}
