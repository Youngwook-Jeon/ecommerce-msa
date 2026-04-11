package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.mapper.ProductAggregateMapper;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;
    private final ProductDataAccessMapper productDataAccessMapper;
    private final ProductAggregateMapper productAggregateMapper;
    private final EntityManager entityManager;

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepository,
                                 CategoryJpaRepository categoryJpaRepository,
                                 ProductDataAccessMapper productDataAccessMapper,
                                 ProductAggregateMapper productAggregateMapper,
                                 EntityManager entityManager) {
        this.productJpaRepository = productJpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
        this.productDataAccessMapper = productDataAccessMapper;
        this.productAggregateMapper = productAggregateMapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void insert(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product must not be null.");
        }
        if (product.getId() == null) {
            throw new IllegalArgumentException("product id must not be null for insert.");
        }

        CategoryEntity categoryRef = product.getCategoryId()
                .map(id -> categoryJpaRepository.getReferenceById(id.getValue()))
                .orElse(null);

        ProductEntity toPersist = productDataAccessMapper.productToProductEntity(product, categoryRef);
        entityManager.persist(toPersist);
    }

    @Override
    @Transactional
    public void update(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product must not be null.");
        }
        if (product.getId() == null) {
            throw new IllegalArgumentException("product id must not be null for update.");
        }

        CategoryEntity categoryRef = product.getCategoryId()
                .map(id -> categoryJpaRepository.getReferenceById(id.getValue()))
                .orElse(null);

        UUID id = product.getId().getValue();

        ProductEntity current = productJpaRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));

        productDataAccessMapper.updateEntityFromDomain(product, current, categoryRef);
        // Let dirty checking flush changes on commit.
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null.");
        }

        return productJpaRepository.findAggregateById(productId.getValue())
                .map(productAggregateMapper::toProduct);
    }

    @Override
    public boolean existsBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be null.");
        }

        return productJpaRepository.existsVariantSku(sku);
    }
}
