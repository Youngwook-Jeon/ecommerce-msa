package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.ProductImagePersistencePort;
import com.project.young.productservice.dataaccess.entity.ProductImageEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import com.project.young.productservice.dataaccess.repository.ProductImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.domain.valueobject.ProductImageRole;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class ProductImagePersistenceAdapter implements ProductImagePersistencePort {

    private final ProductImageJpaRepository productImageJpaRepository;
    private final ProductJpaRepository productJpaRepository;

    public ProductImagePersistenceAdapter(
            ProductImageJpaRepository productImageJpaRepository,
            ProductJpaRepository productJpaRepository
    ) {
        this.productImageJpaRepository = productImageJpaRepository;
        this.productJpaRepository = productJpaRepository;
    }

    @Override
    public UUID insert(
            UUID productId,
            String storageKey,
            String publicUrl,
            ProductImageRole role,
            int sortOrder,
            String contentType,
            Long fileSize
    ) {
        ProductEntity product = productJpaRepository.getReferenceById(productId);
        ProductImageEntity entity = ProductImageEntity.builder()
                .product(product)
                .storageKey(storageKey)
                .publicUrl(publicUrl)
                .role(toEntityRole(role))
                .sortOrder(sortOrder)
                .contentType(contentType)
                .fileSize(fileSize)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        ProductImageEntity saved = productImageJpaRepository.save(entity);
        return saved.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductImageRow> findActiveByIdAndProduct(UUID imageId, UUID productId) {
        return productImageJpaRepository.findByIdAndProduct_Id(imageId, productId)
                .filter(e -> e.getStatus() == OptionStatusEntity.ACTIVE)
                .map(this::toRow);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductImageRow> findByStorageKeyAndProductId(String storageKey, UUID productId) {
        return productImageJpaRepository.findByStorageKeyAndProduct_Id(storageKey, productId)
                .filter(e -> e.getStatus() == OptionStatusEntity.ACTIVE)
                .map(this::toRow);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageRow> findAllActiveByProductId(UUID productId) {
        return productImageJpaRepository.findByProduct_IdAndStatusOrderBySortOrderAsc(
                        productId,
                        OptionStatusEntity.ACTIVE
                ).stream()
                .sorted(java.util.Comparator
                        .comparing((ProductImageEntity e) -> e.getRole() == ProductImageRoleEntity.MAIN ? 0 : 1)
                        .thenComparingInt(ProductImageEntity::getSortOrder))
                .map(this::toRow)
                .toList();
    }

    @Override
    public int softDelete(UUID imageId, UUID productId) {
        return productImageJpaRepository.softDelete(
                imageId,
                productId,
                OptionStatusEntity.ACTIVE,
                OptionStatusEntity.DELETED
        );
    }

    @Override
    public void demoteActiveMainsToGallery(UUID productId) {
        productImageJpaRepository.demoteActiveMainsToGallery(
                productId,
                ProductImageRoleEntity.GALLERY,
                ProductImageRoleEntity.MAIN,
                OptionStatusEntity.ACTIVE
        );
    }

    @Override
    public void updateRole(UUID imageId, UUID productId, ProductImageRole newRole) {
        int updated = productImageJpaRepository.updateRole(
                imageId,
                productId,
                toEntityRole(newRole),
                OptionStatusEntity.ACTIVE
        );
        if (updated == 0) {
            throw new IllegalStateException("Failed to update image role: " + imageId);
        }
    }

    @Override
    public int updateSortOrder(UUID imageId, UUID productId, int sortOrder) {
        return productImageJpaRepository.updateSortOrder(
                imageId,
                productId,
                sortOrder,
                OptionStatusEntity.ACTIVE
        );
    }

    private ProductImageRow toRow(ProductImageEntity e) {
        return new ProductImageRow(
                e.getId(),
                e.getStorageKey(),
                e.getPublicUrl(),
                toDomainRole(e.getRole()),
                e.getSortOrder()
        );
    }

    private static ProductImageRoleEntity toEntityRole(ProductImageRole role) {
        return switch (role) {
            case MAIN -> ProductImageRoleEntity.MAIN;
            case GALLERY -> ProductImageRoleEntity.GALLERY;
        };
    }

    private static ProductImageRole toDomainRole(ProductImageRoleEntity role) {
        return switch (role) {
            case MAIN -> ProductImageRole.MAIN;
            case GALLERY -> ProductImageRole.GALLERY;
        };
    }
}
