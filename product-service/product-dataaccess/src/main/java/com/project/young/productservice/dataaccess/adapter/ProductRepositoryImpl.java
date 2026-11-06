package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductAttributeEntity;
import com.project.young.productservice.dataaccess.repository.ProductAttributeJpaRepository;
import com.project.young.productservice.dataaccess.mapper.ProductAggregateMapper;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import com.project.young.productservice.dataaccess.catalog.ProductCatalogRevisionStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.HashSet;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;
    private final ProductDataAccessMapper productDataAccessMapper;
    private final ProductAggregateMapper productAggregateMapper;
    private final EntityManager entityManager;
    private final ProductCatalogRevisionStore revisions;
    private final ProductAttributeJpaRepository attributes;

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepository,
                                 CategoryJpaRepository categoryJpaRepository,
                                 ProductDataAccessMapper productDataAccessMapper,
                                 ProductAggregateMapper productAggregateMapper,
                                 EntityManager entityManager, ProductCatalogRevisionStore revisions,
                                 ProductAttributeJpaRepository attributes) {
        this.productJpaRepository = productJpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
        this.productDataAccessMapper = productDataAccessMapper;
        this.productAggregateMapper = productAggregateMapper;
        this.entityManager = entityManager;
        this.revisions = revisions;
        this.attributes = attributes;
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
        synchronizeAttributes(product);
        entityManager.flush();
        revisions.initialize(product.getId().getValue());
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
        synchronizeAttributes(product);
        // Let dirty checking flush changes on commit.
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null.");
        }

        return productJpaRepository.findAggregateById(productId.getValue())
                .map(entity -> productAggregateMapper.toProduct(entity,
                        attributes.findAllByProductIdOrderByAttributeIdAsc(productId.getValue())));
    }

    private void synchronizeAttributes(Product product) {
        var current = attributes.findAllByProductIdOrderByAttributeIdAsc(product.getId().getValue());
        var retained = new HashSet<UUID>();
        for (var attribute : product.getAttributes()) {
            var entity = current.stream().filter(candidate -> candidate.getId().equals(attribute.getId().getValue()))
                    .findFirst().orElse(null);
            if (entity == null) {
                entity = new ProductAttributeEntity();
                entity.setId(attribute.getId().getValue());
                entity.setProductId(product.getId().getValue());
                entity.setAttributeId(attribute.getAttributeId().getValue());
                entity.setAttributeValueId(attribute.getAttributeValueId().getValue());
                entityManager.persist(entity);
            } else {
                entity.setAttributeValueId(attribute.getAttributeValueId().getValue());
            }
            retained.add(entity.getId());
        }
        current.stream().filter(entity -> !retained.contains(entity.getId())).forEach(entityManager::remove);
    }

    @Override
    public boolean existsBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be null.");
        }

        return productJpaRepository.existsVariantSku(sku);
    }
}
