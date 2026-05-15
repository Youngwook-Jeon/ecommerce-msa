package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.ProductOptionValueImagePersistencePort;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueImageEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueJpaRepository;
import com.project.young.productservice.domain.valueobject.ProductImageRole;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class ProductOptionValueImagePersistenceAdapter implements ProductOptionValueImagePersistencePort {

    private final ProductOptionValueImageJpaRepository imageJpaRepository;
    private final ProductOptionValueJpaRepository productOptionValueJpaRepository;

    public ProductOptionValueImagePersistenceAdapter(
            ProductOptionValueImageJpaRepository imageJpaRepository,
            ProductOptionValueJpaRepository productOptionValueJpaRepository
    ) {
        this.imageJpaRepository = imageJpaRepository;
        this.productOptionValueJpaRepository = productOptionValueJpaRepository;
    }

    @Override
    public UUID insert(
            UUID productOptionValueId,
            String storageKey,
            String publicUrl,
            ProductImageRole role,
            int sortOrder,
            String contentType,
            Long fileSize
    ) {
        ProductOptionValueEntity pov = productOptionValueJpaRepository.getReferenceById(productOptionValueId);
        ProductOptionValueImageEntity entity = ProductOptionValueImageEntity.builder()
                .productOptionValue(pov)
                .storageKey(storageKey)
                .publicUrl(publicUrl)
                .role(toEntityRole(role))
                .sortOrder(sortOrder)
                .contentType(contentType)
                .fileSize(fileSize)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        return imageJpaRepository.save(entity).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductImageRow> findActiveByIdAndProductOptionValue(UUID imageId, UUID productOptionValueId) {
        return imageJpaRepository.findByIdAndProductOptionValue_Id(imageId, productOptionValueId)
                .filter(e -> e.getStatus() == OptionStatusEntity.ACTIVE)
                .map(this::toRow);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductImageRow> findByStorageKeyAndProductOptionValueId(String storageKey, UUID productOptionValueId) {
        return imageJpaRepository.findByStorageKeyAndProductOptionValue_Id(storageKey, productOptionValueId)
                .filter(e -> e.getStatus() == OptionStatusEntity.ACTIVE)
                .map(this::toRow);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageRow> findAllActiveByProductOptionValueId(UUID productOptionValueId) {
        return imageJpaRepository.findByProductOptionValue_IdAndStatusOrderBySortOrderAsc(
                        productOptionValueId,
                        OptionStatusEntity.ACTIVE
                ).stream()
                .sorted(Comparator
                        .comparing((ProductOptionValueImageEntity e) -> e.getRole() == ProductImageRoleEntity.MAIN ? 0 : 1)
                        .thenComparingInt(ProductOptionValueImageEntity::getSortOrder))
                .map(this::toRow)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<ProductImageRow>> findAllActiveByProductOptionValueIds(List<UUID> productOptionValueIds) {
        if (productOptionValueIds == null || productOptionValueIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ProductImageRow>> grouped = new HashMap<>();
        for (UUID id : productOptionValueIds) {
            grouped.put(id, new ArrayList<>());
        }
        imageJpaRepository.findByProductOptionValue_IdInAndStatusOrderBySortOrderAsc(
                        productOptionValueIds,
                        OptionStatusEntity.ACTIVE
                ).stream()
                .sorted(Comparator
                        .comparing((ProductOptionValueImageEntity e) -> e.getRole() == ProductImageRoleEntity.MAIN ? 0 : 1)
                        .thenComparingInt(ProductOptionValueImageEntity::getSortOrder))
                .forEach(entity -> grouped
                        .computeIfAbsent(entity.getProductOptionValue().getId(), ignored -> new ArrayList<>())
                        .add(toRow(entity)));
        return grouped;
    }

    @Override
    public int softDelete(UUID imageId, UUID productOptionValueId) {
        return imageJpaRepository.softDelete(
                imageId,
                productOptionValueId,
                OptionStatusEntity.ACTIVE,
                OptionStatusEntity.DELETED
        );
    }

    @Override
    public void demoteActiveMainsToGallery(UUID productOptionValueId) {
        imageJpaRepository.demoteActiveMainsToGallery(
                productOptionValueId,
                ProductImageRoleEntity.GALLERY,
                ProductImageRoleEntity.MAIN,
                OptionStatusEntity.ACTIVE
        );
    }

    @Override
    public void updateRole(UUID imageId, UUID productOptionValueId, ProductImageRole newRole) {
        int updated = imageJpaRepository.updateRole(
                imageId,
                productOptionValueId,
                toEntityRole(newRole),
                OptionStatusEntity.ACTIVE
        );
        if (updated == 0) {
            throw new IllegalStateException("Failed to update option value image role: " + imageId);
        }
    }

    @Override
    public int updateSortOrder(UUID imageId, UUID productOptionValueId, int sortOrder) {
        return imageJpaRepository.updateSortOrder(
                imageId,
                productOptionValueId,
                sortOrder,
                OptionStatusEntity.ACTIVE
        );
    }

    private ProductImageRow toRow(ProductOptionValueImageEntity e) {
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
