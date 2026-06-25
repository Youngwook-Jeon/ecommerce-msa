package com.project.young.productservice.application.service;

import com.project.young.productservice.application.port.output.PublicProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadCartCatalogLineView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PublicCartCatalogQueryService {

    public static final int MAX_VARIANT_IDS = 50;

    private final PublicProductReadRepository publicProductReadRepository;

    public PublicCartCatalogQueryService(PublicProductReadRepository publicProductReadRepository) {
        this.publicProductReadRepository = publicProductReadRepository;
    }

    public List<ReadCartCatalogLineView> resolveCartLines(List<UUID> productVariantIds) {
        if (productVariantIds == null || productVariantIds.isEmpty()) {
            return List.of();
        }
        if (productVariantIds.size() > MAX_VARIANT_IDS) {
            throw new IllegalArgumentException("productVariantId count must be <= " + MAX_VARIANT_IDS);
        }
        if (productVariantIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("productVariantId must not be null");
        }

        List<UUID> distinctVariantIds = productVariantIds.stream().distinct().toList();
        return publicProductReadRepository.findCartCatalogLinesByVariantIds(distinctVariantIds);
    }
}
