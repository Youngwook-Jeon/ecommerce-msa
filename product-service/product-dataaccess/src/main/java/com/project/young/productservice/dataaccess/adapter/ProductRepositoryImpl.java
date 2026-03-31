package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.ProductRepository;
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

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepository,
                                 CategoryJpaRepository categoryJpaRepository,
                                 ProductDataAccessMapper productDataAccessMapper) {
        this.productJpaRepository = productJpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
        this.productDataAccessMapper = productDataAccessMapper;
    }

    @Override
    @Transactional
    public Product insert(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product must not be null.");
        }
        if (product.getId() == null) {
            throw new IllegalArgumentException("product id must not be null for insert.");
        }

        CategoryEntity categoryRef = product.getCategoryId()
                .map(id -> categoryJpaRepository.getReferenceById(id.getValue()))
                .orElse(null);

        ProductEntity toSave = productDataAccessMapper.productToProductEntity(product, categoryRef);
        ProductEntity saved = productJpaRepository.save(toSave);
        return productDataAccessMapper.productEntityToProduct(saved);
    }

    @Override
    @Transactional
    public Product update(Product product) {
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
        ProductEntity current = productJpaRepository.findAggregateById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
        productDataAccessMapper.updateEntityFromDomain(product, current, categoryRef);

        // Let dirty checking flush changes on commit.
        return productDataAccessMapper.productEntityToProduct(current);
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null.");
        }

        return productJpaRepository.findAggregateById(productId.getValue())
                .map(productDataAccessMapper::productEntityToProduct);
    }

    @Override
    public boolean existsBySku(String sku) {
        if(sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be null.");
        }

        return productJpaRepository.existsVariantSku(sku);
    }
}
