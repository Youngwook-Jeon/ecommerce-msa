package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.command.UpdateProductOptionGroupVisualCommand;
import com.project.young.productservice.application.dto.event.ProductCatalogChangeType;
import com.project.young.productservice.application.dto.result.UpdateProductOptionGroupVisualResult;
import com.project.young.productservice.application.port.output.ProductOptionGroupVisualPort;
import com.project.young.productservice.application.port.output.VariantMainImageSyncPort;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class ProductOptionGroupVisualApplicationService {

    private final ProductRepository productRepository;
    private final ProductOptionGroupVisualPort productOptionGroupVisualPort;
    private final VariantMainImageSyncPort variantMainImageSyncPort;
    private final ProductCatalogChangeService productCatalogChangeService;

    public ProductOptionGroupVisualApplicationService(
            ProductRepository productRepository,
            ProductOptionGroupVisualPort productOptionGroupVisualPort,
            VariantMainImageSyncPort variantMainImageSyncPort,
            ProductCatalogChangeService productCatalogChangeService
    ) {
        this.productRepository = productRepository;
        this.productOptionGroupVisualPort = productOptionGroupVisualPort;
        this.variantMainImageSyncPort = variantMainImageSyncPort;
        this.productCatalogChangeService = productCatalogChangeService;
    }

    @Transactional
    public UpdateProductOptionGroupVisualResult updateVisualFlag(
            UUID productId,
            UUID productOptionGroupId,
            UpdateProductOptionGroupVisualCommand command, long expectedVersion) {
        if (command == null) {
            throw new IllegalArgumentException("UpdateProductOptionGroupVisualCommand is required.");
        }
        productCatalogChangeService.verifyVersion(productId, expectedVersion);
        Product product = productRepository.findById(new ProductId(productId))
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
        if (product.isDeleted()) {
            throw new ProductDomainException("Cannot update visual option group for a deleted product.");
        }

        productCatalogChangeService.recordChange(product, ProductCatalogChangeType.OPTION_CHANGED, expectedVersion);
        productOptionGroupVisualPort.setDrivesVariantImages(
                productId,
                productOptionGroupId,
                command.isDrivesVariantImages()
        );
        variantMainImageSyncPort.syncAllForProduct(productId);

        return UpdateProductOptionGroupVisualResult.builder()
                .productId(productId)
                .productOptionGroupId(productOptionGroupId)
                .drivesVariantImages(command.isDrivesVariantImages())
                .build();
    }
}
