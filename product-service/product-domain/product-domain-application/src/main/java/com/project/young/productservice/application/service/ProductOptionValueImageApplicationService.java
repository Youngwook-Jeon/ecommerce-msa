package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.command.CommitProductImageCommand;
import com.project.young.productservice.application.dto.event.ProductCatalogChangeType;
import com.project.young.productservice.application.dto.command.PresignProductImageUploadCommand;
import com.project.young.productservice.application.dto.command.ReorderProductImagesCommand;
import com.project.young.productservice.application.dto.result.CommitProductImageResult;
import com.project.young.productservice.application.dto.result.PresignProductImageUploadResult;
import com.project.young.productservice.application.dto.result.ReorderProductImagesResult;
import com.project.young.productservice.application.port.output.ProductOptionValueImagePersistencePort;
import com.project.young.productservice.application.port.output.ProductImageStoragePort;
import com.project.young.productservice.application.port.output.VariantMainImageSyncPort;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.domain.valueobject.ProductImageRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class ProductOptionValueImageApplicationService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(5);
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_GALLERY_IMAGES = 30;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final ProductRepository productRepository;
    private final ProductOptionValueImagePersistencePort imagePersistence;
    private final ProductImageStoragePort productImageStorage;
    private final ProductOptionValueOwnershipValidator ownershipValidator;
    private final VariantMainImageSyncPort variantMainImageSyncPort;
    private final StorefrontProductCatalogInvalidationService storefrontProductCatalogInvalidationService;

    public ProductOptionValueImageApplicationService(
            ProductRepository productRepository,
            ProductOptionValueImagePersistencePort imagePersistence,
            ProductImageStoragePort productImageStorage,
            ProductOptionValueOwnershipValidator ownershipValidator,
            VariantMainImageSyncPort variantMainImageSyncPort,
            StorefrontProductCatalogInvalidationService storefrontProductCatalogInvalidationService
    ) {
        this.productRepository = productRepository;
        this.imagePersistence = imagePersistence;
        this.productImageStorage = productImageStorage;
        this.ownershipValidator = ownershipValidator;
        this.variantMainImageSyncPort = variantMainImageSyncPort;
        this.storefrontProductCatalogInvalidationService = storefrontProductCatalogInvalidationService;
    }

    @Transactional(readOnly = true)
    public PresignProductImageUploadResult presignUpload(
            UUID productId,
            UUID productOptionValueId,
            PresignProductImageUploadCommand command
    ) {
        validateProductWritable(productId);
        ownershipValidator.requireOwnedByProduct(productId, productOptionValueId);
        validateContent(command.getContentType(), command.getContentLength());
        validateRole(command.getRole());

        if (command.getRole() == ProductImageRole.GALLERY) {
            long galleryCount = imagePersistence.findAllActiveByProductOptionValueId(productOptionValueId).stream()
                    .filter(row -> row.role() == ProductImageRole.GALLERY)
                    .count();
            if (galleryCount >= MAX_GALLERY_IMAGES) {
                throw new ProductDomainException("Maximum number of gallery images reached: " + MAX_GALLERY_IMAGES);
            }
        }

        String safeName = sanitizeFileName(command.getFileName());
        String objectKey = buildObjectKey(productId, productOptionValueId, safeName);

        ProductImageStoragePort.PresignedPutResult signed = productImageStorage.presignPut(
                objectKey,
                command.getContentType(),
                command.getContentLength(),
                PRESIGN_TTL
        );

        return PresignProductImageUploadResult.builder()
                .uploadUrl(signed.uploadUrl())
                .httpMethod(signed.httpMethod())
                .headers(signed.headers())
                .objectKey(objectKey)
                .publicUrl(productImageStorage.publicUrlForKey(objectKey))
                .expiresAt(signed.expiresAt())
                .build();
    }

    @Transactional
    public CommitProductImageResult commitUpload(
            UUID productId,
            UUID productOptionValueId,
            CommitProductImageCommand command
    ) {
        Product product = findProductOrThrow(productId);
        ownershipValidator.requireOwnedByProduct(productId, productOptionValueId);
        validateContent(command.getContentType(), command.getFileSize());
        validateRole(command.getRole());
        validateObjectKeyBelongsToProductOptionValue(productId, productOptionValueId, command.getObjectKey());

        Optional<ProductOptionValueImagePersistencePort.ProductImageRow> existing =
                imagePersistence.findByStorageKeyAndProductOptionValueId(command.getObjectKey(), productOptionValueId);
        if (existing.isPresent()) {
            return toIdempotentCommitResult(command, existing.get());
        }

        if (command.getRole() == ProductImageRole.GALLERY) {
            long galleryCount = imagePersistence.findAllActiveByProductOptionValueId(productOptionValueId).stream()
                    .filter(row -> row.role() == ProductImageRole.GALLERY)
                    .count();
            if (galleryCount >= MAX_GALLERY_IMAGES) {
                throw new ProductDomainException("Maximum number of gallery images reached: " + MAX_GALLERY_IMAGES);
            }
        }

        String publicUrl = productImageStorage.publicUrlForKey(command.getObjectKey());
        if (command.getRole() == ProductImageRole.MAIN) {
            imagePersistence.demoteActiveMainsToGallery(productOptionValueId);
        }

        UUID imageId;
        try {
            imageId = imagePersistence.insert(
                    productOptionValueId,
                    command.getObjectKey(),
                    publicUrl,
                    command.getRole(),
                    command.getSortOrder(),
                    command.getContentType(),
                    command.getFileSize()
            );
        } catch (RuntimeException ex) {
            Optional<ProductOptionValueImagePersistencePort.ProductImageRow> raced =
                    imagePersistence.findByStorageKeyAndProductOptionValueId(command.getObjectKey(), productOptionValueId);
            if (raced.isPresent()) {
                return toIdempotentCommitResult(command, raced.get());
            }
            throw ex;
        }

        variantMainImageSyncPort.syncByProductOptionValueId(productOptionValueId);
        invalidateStorefrontCatalog(product);

        return CommitProductImageResult.builder()
                .id(imageId)
                .publicUrl(publicUrl)
                .role(command.getRole().name())
                .sortOrder(command.getSortOrder())
                .build();
    }

    @Transactional
    public void deleteImage(UUID productId, UUID productOptionValueId, UUID imageId) {
        executeWithMainImagePolicy(productId, productOptionValueId, () -> {
            imagePersistence.findActiveByIdAndProductOptionValue(imageId, productOptionValueId)
                    .orElseThrow(() -> new ProductDomainException("Option value image not found."));
            int deleted = imagePersistence.softDelete(imageId, productOptionValueId);
            if (deleted == 0) {
                throw new ProductDomainException("Option value image not found or already deleted.");
            }
        });
    }

    @Transactional
    public ReorderProductImagesResult reorderImages(
            UUID productId,
            UUID productOptionValueId,
            ReorderProductImagesCommand command
    ) {
        return executeWithMainImagePolicy(productId, productOptionValueId, () -> {
            if (command == null || command.getOrderedImageIds() == null) {
                throw new ProductDomainException("orderedImageIds is required.");
            }
            List<UUID> requested = command.getOrderedImageIds();
            List<ProductOptionValueImagePersistencePort.ProductImageRow> activeRows =
                    imagePersistence.findAllActiveByProductOptionValueId(productOptionValueId);
            List<UUID> activeIds = activeRows.stream()
                    .map(ProductOptionValueImagePersistencePort.ProductImageRow::id)
                    .toList();

            if (requested.size() != new HashSet<>(requested).size()) {
                throw new ProductDomainException("orderedImageIds contains duplicates.");
            }
            if (requested.size() != activeIds.size()) {
                throw new ProductDomainException("orderedImageIds must include all active option value images.");
            }
            if (!new HashSet<>(requested).equals(new HashSet<>(activeIds))) {
                throw new ProductDomainException("orderedImageIds must match active option value images exactly.");
            }

            for (int i = 0; i < requested.size(); i++) {
                UUID imageId = requested.get(i);
                int updated = imagePersistence.updateSortOrder(imageId, productOptionValueId, i);
                if (updated == 0) {
                    throw new ProductDomainException("Failed to update image sort order: " + imageId);
                }
            }

            return ReorderProductImagesResult.builder()
                    .productId(productId)
                    .reorderedCount(requested.size())
                    .orderedImageIds(List.copyOf(requested))
                    .build();
        });
    }

    private <T> T executeWithMainImagePolicy(UUID productId, UUID productOptionValueId, ImageMutation<T> mutation) {
        Product product = findProductOrThrow(productId);
        ownershipValidator.requireOwnedByProduct(productId, productOptionValueId);
        T result = mutation.apply();
        applyFirstActiveImageAsMainPolicy(productOptionValueId);
        variantMainImageSyncPort.syncByProductOptionValueId(productOptionValueId);
        invalidateStorefrontCatalog(product);
        return result;
    }

    private void executeWithMainImagePolicy(UUID productId, UUID productOptionValueId, Runnable mutation) {
        executeWithMainImagePolicy(productId, productOptionValueId, () -> {
            mutation.run();
            return null;
        });
    }

    private void applyFirstActiveImageAsMainPolicy(UUID productOptionValueId) {
        List<ProductOptionValueImagePersistencePort.ProductImageRow> activeRows =
                imagePersistence.findAllActiveByProductOptionValueId(productOptionValueId).stream()
                        .sorted(Comparator.comparingInt(ProductOptionValueImagePersistencePort.ProductImageRow::sortOrder))
                        .toList();

        if (activeRows.isEmpty()) {
            return;
        }

        ProductOptionValueImagePersistencePort.ProductImageRow first = activeRows.getFirst();
        imagePersistence.demoteActiveMainsToGallery(productOptionValueId);
        imagePersistence.updateRole(first.id(), productOptionValueId, ProductImageRole.MAIN);
    }

    private CommitProductImageResult toIdempotentCommitResult(
            CommitProductImageCommand command,
            ProductOptionValueImagePersistencePort.ProductImageRow existing
    ) {
        String expectedPublicUrl = productImageStorage.publicUrlForKey(command.getObjectKey());
        if (!existing.storageKey().equals(command.getObjectKey())) {
            throw new ProductDomainException("Object key mismatch for idempotent commit.");
        }
        if (existing.role() != command.getRole()) {
            throw new ProductDomainException("Image already committed with a different role.");
        }
        if (existing.sortOrder() != command.getSortOrder()) {
            throw new ProductDomainException("Image already committed with a different sort order.");
        }
        if (!existing.publicUrl().equals(expectedPublicUrl)) {
            throw new ProductDomainException("Image already committed with inconsistent public URL.");
        }

        return CommitProductImageResult.builder()
                .id(existing.id())
                .publicUrl(existing.publicUrl())
                .role(existing.role().name())
                .sortOrder(existing.sortOrder())
                .build();
    }

    private void invalidateStorefrontCatalog(Product product) {
        storefrontProductCatalogInvalidationService.invalidate(product, ProductCatalogChangeType.IMAGE_CHANGED);
    }

    @FunctionalInterface
    private interface ImageMutation<T> {
        T apply();
    }

    private Product findProductOrThrow(UUID productId) {
        Product product = productRepository.findById(new ProductId(productId))
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
        if (product.isDeleted()) {
            throw new ProductDomainException("Cannot manage images for a deleted product.");
        }
        return product;
    }

    private void validateProductWritable(UUID productId) {
        findProductOrThrow(productId);
    }

    private void validateContent(String contentType, long contentLength) {
        if (contentType == null || contentType.isBlank()) {
            throw new ProductDomainException("Content type is required.");
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new ProductDomainException("Unsupported image content type: " + contentType);
        }
        if (contentLength <= 0 || contentLength > MAX_BYTES) {
            throw new ProductDomainException("Invalid file size. Max allowed is " + MAX_BYTES + " bytes.");
        }
    }

    private void validateRole(ProductImageRole role) {
        if (role == null) {
            throw new ProductDomainException("Image role is required.");
        }
    }

    private void validateObjectKeyBelongsToProductOptionValue(
            UUID productId,
            UUID productOptionValueId,
            String objectKey
    ) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new ProductDomainException("Object key is required.");
        }
        String expectedPrefix = "products/" + productId + "/option-values/" + productOptionValueId + "/";
        if (!objectKey.startsWith(expectedPrefix)) {
            throw new ProductDomainException("Object key does not belong to this product option value.");
        }
    }

    private String buildObjectKey(UUID productId, UUID productOptionValueId, String safeFileName) {
        YearMonth ym = YearMonth.now(ZoneOffset.UTC);
        String ymPart = ym.getYear() + "/" + String.format("%02d", ym.getMonthValue());
        return "products/" + productId + "/option-values/" + productOptionValueId + "/" + ymPart + "/"
                + UUID.randomUUID() + "-" + safeFileName;
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "image.bin";
        }

        String base = fileName.replace("\\", "/");
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }

        String ext = "";
        int dot = base.lastIndexOf('.');
        if (dot > 0 && dot < base.length() - 1) {
            ext = base.substring(dot);
            base = base.substring(0, dot);
        }

        base = base.replaceAll("[^a-zA-Z0-9_-]", "");
        if (base.isEmpty()) {
            base = "upload";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        return base + ext;
    }
}
