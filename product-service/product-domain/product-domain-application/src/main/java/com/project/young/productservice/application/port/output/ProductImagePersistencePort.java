package com.project.young.productservice.application.port.output;

import com.project.young.productservice.domain.valueobject.ProductImageRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImagePersistencePort {

    UUID insert(UUID productId, String storageKey, String publicUrl, ProductImageRole role, int sortOrder,
                String contentType, Long fileSize);

    Optional<ProductImageRow> findActiveByIdAndProduct(UUID imageId, UUID productId);

    Optional<ProductImageRow> findByStorageKeyAndProductId(String storageKey, UUID productId);

    List<ProductImageRow> findAllActiveByProductId(UUID productId);

    /**
     * @return number of rows updated (1 expected)
     */
    int softDelete(UUID imageId, UUID productId);

    void demoteActiveMainsToGallery(UUID productId);

    void updateRole(UUID imageId, UUID productId, ProductImageRole newRole);

    record ProductImageRow(
            UUID id,
            String storageKey,
            String publicUrl,
            ProductImageRole role,
            int sortOrder
    ) {
    }
}
