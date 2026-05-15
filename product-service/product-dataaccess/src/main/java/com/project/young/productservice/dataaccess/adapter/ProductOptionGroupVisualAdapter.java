package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.ProductOptionGroupVisualPort;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.repository.ProductOptionGroupJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class ProductOptionGroupVisualAdapter implements ProductOptionGroupVisualPort {

    private final ProductOptionGroupJpaRepository productOptionGroupJpaRepository;

    public ProductOptionGroupVisualAdapter(ProductOptionGroupJpaRepository productOptionGroupJpaRepository) {
        this.productOptionGroupJpaRepository = productOptionGroupJpaRepository;
    }

    @Override
    public void setDrivesVariantImages(UUID productId, UUID productOptionGroupId, boolean drivesVariantImages) {
        if (drivesVariantImages) {
            productOptionGroupJpaRepository.clearVisualFlagsForProduct(productId, OptionStatusEntity.ACTIVE);
        }
        int updated = productOptionGroupJpaRepository.updateDrivesVariantImages(
                productId,
                productOptionGroupId,
                drivesVariantImages,
                OptionStatusEntity.ACTIVE
        );
        if (updated == 0) {
            throw new IllegalStateException("Failed to update visual option group flag: " + productOptionGroupId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findActiveVisualGroupId(UUID productId) {
        return productOptionGroupJpaRepository.findActiveVisualGroupByProductId(productId, OptionStatusEntity.ACTIVE)
                .map(group -> group.getId());
    }
}
