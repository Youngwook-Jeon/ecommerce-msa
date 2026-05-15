package com.project.young.productservice.application.port.output;

import com.project.young.productservice.domain.valueobject.ProductImageRole;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProductOptionValueImagePersistencePort {

    UUID insert(
            UUID productOptionValueId,
            String storageKey,
            String publicUrl,
            ProductImageRole role,
            int sortOrder,
            String contentType,
            Long fileSize
    );

    Optional<ProductImageRow> findActiveByIdAndProductOptionValue(UUID imageId, UUID productOptionValueId);

    Optional<ProductImageRow> findByStorageKeyAndProductOptionValueId(String storageKey, UUID productOptionValueId);

    List<ProductImageRow> findAllActiveByProductOptionValueId(UUID productOptionValueId);

    Map<UUID, List<ProductImageRow>> findAllActiveByProductOptionValueIds(List<UUID> productOptionValueIds);

    int softDelete(UUID imageId, UUID productOptionValueId);

    void demoteActiveMainsToGallery(UUID productOptionValueId);

    void updateRole(UUID imageId, UUID productOptionValueId, ProductImageRole newRole);

    int updateSortOrder(UUID imageId, UUID productOptionValueId, int sortOrder);

    record ProductImageRow(
            UUID id,
            String storageKey,
            String publicUrl,
            ProductImageRole role,
            int sortOrder
    ) {
    }
}
