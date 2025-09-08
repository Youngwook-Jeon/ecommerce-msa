package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final ProductDataAccessMapper productDataAccessMapper;

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepository,
                                 ProductDataAccessMapper productDataAccessMapper) {
        this.productJpaRepository = productJpaRepository;
        this.productDataAccessMapper = productDataAccessMapper;
    }

    @Override
    public Product save(Product product) {
        ProductEntity productEntity = productDataAccessMapper.productToProductEntity(product);
        ProductEntity savedEntity = productJpaRepository.save(productEntity);
        log.debug("Product saved with id: {}", savedEntity.getId());
        return productDataAccessMapper.productEntityToProduct(savedEntity);
    }

    @Override
    public List<Product> saveAll(List<Product> products) {
        List<ProductEntity> productEntities = products.stream()
                .map(productDataAccessMapper::productToProductEntity)
                .collect(Collectors.toList());

        List<ProductEntity> savedEntities = productJpaRepository.saveAll(productEntities);
        log.debug("Saved {} products", savedEntities.size());

        return savedEntities.stream()
                .map(productDataAccessMapper::productEntityToProduct)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        return productJpaRepository.findById(productId.getValue())
                .map(productDataAccessMapper::productEntityToProduct);
    }

    @Override
    public List<Product> findAllById(List<ProductId> productIds) {
        List<UUID> ids = productIds.stream()
                .map(ProductId::getValue)
                .collect(Collectors.toList());

        return productJpaRepository.findAllById(ids).stream()
                .map(productDataAccessMapper::productEntityToProduct)
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findByStatus(String status) {
        return List.of();
    }

    @Override
    public List<Product> findByStatusIn(List<String> statuses) {
        return List.of();
    }

    @Override
    public boolean existsByName(String name) {
        return productJpaRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, ProductId productIdToExclude) {
        return productJpaRepository.existsByNameAndIdNot(name, productIdToExclude.getValue());
    }

    @Override
    public void deleteById(ProductId productId) {

    }

    @Override
    public void updateStatusByIds(String status, List<ProductId> productIds) {

    }

    @Override
    public void updateStatusByCategoryId(String status, CategoryId categoryId) {

    }

    @Override
    public void updateStatusByCategoryIds(String status, List<CategoryId> categoryIds) {

    }

    @Override
    public void updateStatusByBrandId(String status, BrandId brandId) {

    }

    @Override
    public void nullifyCategoryReference(CategoryId categoryId) {

    }

    @Override
    public void nullifyBrandReference(BrandId brandId) {

    }

    @Override
    public long countByStatus(String status) {
        return 0;
    }

    @Override
    public long countByCategoryId(CategoryId categoryId) {
        return 0;
    }

    @Override
    public long countByBrandId(BrandId brandId) {
        return 0;
    }

    @Override
    public List<Product> findByCategoryId(CategoryId categoryId) {
        return productJpaRepository.findByCategoryId(categoryId.getValue()).stream()
                .map(productDataAccessMapper::productEntityToProduct)
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findByCategoryIdAndStatus(CategoryId categoryId, String status) {
        return List.of();
    }

    @Override
    public List<Product> findByCategoryIdIn(List<CategoryId> categoryIds) {
        List<Long> ids = categoryIds.stream()
                .map(CategoryId::getValue)
                .collect(Collectors.toList());

        return productJpaRepository.findByCategoryIdIn(ids).stream()
                .map(productDataAccessMapper::productEntityToProduct)
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findByCategoryIdInAndStatus(List<CategoryId> categoryIds, String status) {
        return List.of();
    }

    @Override
    public List<Product> findByBrandId(BrandId brandId) {
        return productJpaRepository.findByBrandId(brandId.getValue()).stream()
                .map(productDataAccessMapper::productEntityToProduct)
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findByBrandIdAndStatus(BrandId brandId, String status) {
        return List.of();
    }

    @Override
    public List<Product> findByBrandIdIn(List<BrandId> brandIds) {
        List<UUID> ids = brandIds.stream()
                .map(BrandId::getValue)
                .collect(Collectors.toList());

        return productJpaRepository.findByBrandIdIn(ids).stream()
                .map(productDataAccessMapper::productEntityToProduct)
                .collect(Collectors.toList());
    }

    @Override
    public long countByCategoryIdAndStatus(CategoryId categoryId, String status) {
        return productJpaRepository.countByCategoryIdAndStatus(categoryId.getValue(), status);
    }

    @Override
    public long countByBrandIdAndStatus(BrandId brandId, String status) {
        return productJpaRepository.countByBrandIdAndStatus(brandId.getValue(), status);
    }

    @Override
    public void updateStatusForIds(String newStatus, List<ProductId> productIds) {
        List<UUID> ids = productIds.stream()
                .map(ProductId::getValue)
                .collect(Collectors.toList());

        int updatedCount = productJpaRepository.updateStatusByIdIn(newStatus, ids);
        log.info("Updated status to '{}' for {} products", newStatus, updatedCount);
    }
}