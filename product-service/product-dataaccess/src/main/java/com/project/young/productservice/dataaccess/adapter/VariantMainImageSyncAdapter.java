package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.ProductOptionValueImagePersistencePort;
import com.project.young.productservice.application.port.output.VariantMainImageSyncPort;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.entity.VariantOptionValueEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductOptionGroupJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductVariantJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class VariantMainImageSyncAdapter implements VariantMainImageSyncPort {

    private static final String DEFAULT_PRODUCT_IMAGE_URL = "https://placehold.co/600x400?text=Product";

    private final ProductVariantJpaRepository productVariantJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final ProductOptionGroupJpaRepository productOptionGroupJpaRepository;
    private final ProductOptionValueJpaRepository productOptionValueJpaRepository;
    private final ProductOptionValueImagePersistencePort productOptionValueImagePersistence;

    public VariantMainImageSyncAdapter(
            ProductVariantJpaRepository productVariantJpaRepository,
            ProductJpaRepository productJpaRepository,
            ProductOptionGroupJpaRepository productOptionGroupJpaRepository,
            ProductOptionValueJpaRepository productOptionValueJpaRepository,
            ProductOptionValueImagePersistencePort productOptionValueImagePersistence
    ) {
        this.productVariantJpaRepository = productVariantJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.productOptionGroupJpaRepository = productOptionGroupJpaRepository;
        this.productOptionValueJpaRepository = productOptionValueJpaRepository;
        this.productOptionValueImagePersistence = productOptionValueImagePersistence;
    }

    @Override
    public void syncByProductOptionValueId(UUID productOptionValueId) {
        UUID productId = productOptionValueJpaRepository.findProductIdByProductOptionValueId(productOptionValueId)
                .orElseThrow(() -> new IllegalStateException("Product option value not found: " + productOptionValueId));
        UUID povGroupId = productOptionValueJpaRepository.findProductOptionGroupIdByProductOptionValueId(productOptionValueId)
                .orElseThrow(() -> new IllegalStateException("Product option value group not found: " + productOptionValueId));

        Optional<ProductOptionGroupEntity> visualGroup = productOptionGroupJpaRepository.findActiveVisualGroupByProductId(
                productId,
                OptionStatusEntity.ACTIVE
        );
        if (visualGroup.isEmpty() || !visualGroup.get().getId().equals(povGroupId)) {
            return;
        }

        List<UUID> variantIds = productVariantJpaRepository.findAllIdsByProductOptionValueId(productOptionValueId);
        if (variantIds.isEmpty()) {
            return;
        }

        String resolved = resolveMainImageUrlForVisualPov(productOptionValueId, productFallback(productId));
        productVariantJpaRepository.updateMainImageUrlForIds(variantIds, resolved);
    }

    @Override
    public void syncAllForProduct(UUID productId) {
        String productFallbackUrl = productFallback(productId);
        Optional<ProductOptionGroupEntity> visualGroup = productOptionGroupJpaRepository.findActiveVisualGroupByProductId(
                productId,
                OptionStatusEntity.ACTIVE
        );

        List<ProductVariantEntity> variants =
                productVariantJpaRepository.findAllByProductIdWithSelectedOptionValues(productId);
        if (variants.isEmpty()) {
            return;
        }

        if (visualGroup.isEmpty()) {
            productVariantJpaRepository.updateMainImageUrlForIds(
                    variants.stream().map(ProductVariantEntity::getId).toList(),
                    productFallbackUrl
            );
            return;
        }

        UUID visualGroupId = visualGroup.get().getId();
        Map<UUID, UUID> povToGroupId = loadPovToGroupIdMapByProductId(productId);

        List<UUID> visualPovIds = variants.stream()
                .map(variant -> findVisualPovId(variant, visualGroupId, povToGroupId))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, List<ProductOptionValueImagePersistencePort.ProductImageRow>> imagesByPov = visualPovIds.isEmpty()
                ? Map.of()
                : productOptionValueImagePersistence.findAllActiveByProductOptionValueIds(visualPovIds);

        Map<String, List<UUID>> updatesByUrl = new HashMap<>();
        for (ProductVariantEntity variant : variants) {
            UUID visualPovId = findVisualPovId(variant, visualGroupId, povToGroupId);
            String resolved = visualPovId == null
                    ? productFallbackUrl
                    : firstImageUrl(visualPovId, imagesByPov, productFallbackUrl);
            updatesByUrl.computeIfAbsent(resolved, ignored -> new ArrayList<>()).add(variant.getId());
        }

        for (Map.Entry<String, List<UUID>> entry : updatesByUrl.entrySet()) {
            productVariantJpaRepository.updateMainImageUrlForIds(entry.getValue(), entry.getKey());
        }
    }

    @Override
    public void syncForVariant(UUID variantId) {
        ProductVariantEntity variant = productVariantJpaRepository.findByIdWithSelectedOptionValuesAndProduct(variantId)
                .orElseThrow(() -> new IllegalStateException("Variant not found: " + variantId));
        UUID productId = variant.getProduct().getId();
        String resolved = resolveMainImageUrl(variant, productId);
        productVariantJpaRepository.updateMainImageUrl(variantId, resolved);
    }

    private String resolveMainImageUrl(ProductVariantEntity variant, UUID productId) {
        Optional<ProductOptionGroupEntity> visualGroup = productOptionGroupJpaRepository.findActiveVisualGroupByProductId(
                productId,
                OptionStatusEntity.ACTIVE
        );
        if (visualGroup.isEmpty()) {
            return productFallback(productId);
        }

        UUID visualGroupId = visualGroup.get().getId();
        List<UUID> povIds = variant.getSelectedOptionValues().stream()
                .map(VariantOptionValueEntity::getProductOptionValueId)
                .toList();
        Map<UUID, UUID> povToGroupId = loadPovToGroupIdMapByPovIds(povIds);
        UUID visualPovId = findVisualPovId(variant, visualGroupId, povToGroupId);
        if (visualPovId == null) {
            return productFallback(productId);
        }

        return resolveMainImageUrlForVisualPov(visualPovId, productFallback(productId));
    }

    private String resolveMainImageUrlForVisualPov(UUID visualPovId, String productFallbackUrl) {
        Map<UUID, List<ProductOptionValueImagePersistencePort.ProductImageRow>> imagesByPov =
                productOptionValueImagePersistence.findAllActiveByProductOptionValueIds(List.of(visualPovId));
        return firstImageUrl(visualPovId, imagesByPov, productFallbackUrl);
    }

    private static String firstImageUrl(
            UUID povId,
            Map<UUID, List<ProductOptionValueImagePersistencePort.ProductImageRow>> imagesByPov,
            String productFallbackUrl
    ) {
        List<ProductOptionValueImagePersistencePort.ProductImageRow> images = imagesByPov.get(povId);
        if (images == null || images.isEmpty()) {
            return productFallbackUrl;
        }
        return images.getFirst().publicUrl();
    }

    private static UUID findVisualPovId(
            ProductVariantEntity variant,
            UUID visualGroupId,
            Map<UUID, UUID> povToGroupId
    ) {
        if (variant.getSelectedOptionValues() == null || variant.getSelectedOptionValues().isEmpty()) {
            return null;
        }
        for (VariantOptionValueEntity selected : variant.getSelectedOptionValues()) {
            UUID povId = selected.getProductOptionValueId();
            if (visualGroupId.equals(povToGroupId.get(povId))) {
                return povId;
            }
        }
        return null;
    }

    private Map<UUID, UUID> loadPovToGroupIdMapByProductId(UUID productId) {
        return toPovToGroupIdMap(productOptionValueJpaRepository.findPovIdAndGroupIdByProductId(productId));
    }

    private Map<UUID, UUID> loadPovToGroupIdMapByPovIds(List<UUID> povIds) {
        if (povIds.isEmpty()) {
            return Map.of();
        }
        return toPovToGroupIdMap(productOptionValueJpaRepository.findPovIdAndGroupIdByPovIds(povIds));
    }

    private static Map<UUID, UUID> toPovToGroupIdMap(List<Object[]> rows) {
        Map<UUID, UUID> map = HashMap.newHashMap(rows.size());
        for (Object[] row : rows) {
            map.put((UUID) row[0], (UUID) row[1]);
        }
        return map;
    }

    private String productFallback(UUID productId) {
        return productJpaRepository.findById(productId)
                .map(product -> {
                    String url = product.getMainImageUrl();
                    if (url == null || url.isBlank()) {
                        return DEFAULT_PRODUCT_IMAGE_URL;
                    }
                    return url;
                })
                .orElse(DEFAULT_PRODUCT_IMAGE_URL);
    }
}
