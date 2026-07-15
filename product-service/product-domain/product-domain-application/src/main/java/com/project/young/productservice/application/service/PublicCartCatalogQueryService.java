package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.application.port.output.PublicProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadCartCatalogLineView;
import com.project.young.productservice.domain.inventory.InventoryAvailability;
import com.project.young.productservice.domain.repository.InventoryReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PublicCartCatalogQueryService {

    public static final int MAX_VARIANT_IDS = 50;

    private final PublicProductReadRepository publicProductReadRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    public PublicCartCatalogQueryService(
            PublicProductReadRepository publicProductReadRepository,
            InventoryReservationRepository inventoryReservationRepository
    ) {
        this.publicProductReadRepository = publicProductReadRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
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
        List<ReadCartCatalogLineView> lines =
                publicProductReadRepository.findCartCatalogLinesByVariantIds(distinctVariantIds);
        if (lines.isEmpty()) {
            return lines;
        }

        Instant now = Instant.now();
        List<ProductVariantId> variantIds = lines.stream()
                .map(line -> new ProductVariantId(line.productVariantId()))
                .toList();
        Map<UUID, Integer> activeReserved =
                inventoryReservationRepository.sumActiveQuantityByVariantIds(variantIds, now);

        return lines.stream()
                .map(line -> withAvailableStock(line, activeReserved.getOrDefault(line.productVariantId(), 0)))
                .toList();
    }

    private static ReadCartCatalogLineView withAvailableStock(
            ReadCartCatalogLineView line,
            int activeReserved
    ) {
        int available = InventoryAvailability.available(line.stockQuantity(), activeReserved);
        if (available == line.stockQuantity()) {
            return line;
        }
        return ReadCartCatalogLineView.builder()
                .productId(line.productId())
                .productVariantId(line.productVariantId())
                .productName(line.productName())
                .brand(line.brand())
                .sku(line.sku())
                .imageUrl(line.imageUrl())
                .unitPrice(line.unitPrice())
                .purchasable(line.purchasable())
                .stockQuantity(available)
                .variantOptions(line.variantOptions())
                .build();
    }
}
