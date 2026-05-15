package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.command.CommitProductImageCommand;
import com.project.young.productservice.application.dto.command.PresignProductImageUploadCommand;
import com.project.young.productservice.application.dto.command.ReorderProductImagesCommand;
import com.project.young.productservice.application.dto.result.CommitProductImageResult;
import com.project.young.productservice.application.dto.result.PresignProductImageUploadResult;
import com.project.young.productservice.application.dto.result.ReorderProductImagesResult;
import com.project.young.productservice.application.port.output.ProductImagePersistencePort;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

@Service
@Slf4j
public class ProductImageApplicationService {

    public static final String DEFAULT_PRODUCT_IMAGE_URL = "https://placehold.co/600x400?text=Product";

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(5);
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_GALLERY_IMAGES = 30;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final ProductRepository productRepository;
    private final ProductImagePersistencePort productImagePersistence;
    private final ProductImageStoragePort productImageStorage;
    private final VariantMainImageSyncPort variantMainImageSyncPort;

    public ProductImageApplicationService(
            ProductRepository productRepository,
            ProductImagePersistencePort productImagePersistence,
            ProductImageStoragePort productImageStorage,
            VariantMainImageSyncPort variantMainImageSyncPort
    ) {
        this.productRepository = productRepository;
        this.productImagePersistence = productImagePersistence;
        this.productImageStorage = productImageStorage;
        this.variantMainImageSyncPort = variantMainImageSyncPort;
    }

    @Transactional(readOnly = true)
    public PresignProductImageUploadResult presignUpload(UUID productId, PresignProductImageUploadCommand command) {
        validateProductWritable(productId);
        validateContent(command.getContentType(), command.getContentLength());
        validateRole(command.getRole());

        if (command.getRole() == ProductImageRole.GALLERY) {
            long galleryCount = productImagePersistence.findAllActiveByProductId(productId).stream()
                    .filter(row -> row.role() == ProductImageRole.GALLERY)
                    .count();
            if (galleryCount >= MAX_GALLERY_IMAGES) {
                throw new ProductDomainException("Maximum number of gallery images reached: " + MAX_GALLERY_IMAGES);
            }
        }

        String safeName = sanitizeFileName(command.getFileName());
        String objectKey = buildObjectKey(productId, safeName);

        ProductImageStoragePort.PresignedPutResult signed = productImageStorage.presignPut(
                objectKey,
                command.getContentType(),
                command.getContentLength(),
                PRESIGN_TTL
        );

        String publicUrl = productImageStorage.publicUrlForKey(objectKey);

        return PresignProductImageUploadResult.builder()
                .uploadUrl(signed.uploadUrl())
                .httpMethod(signed.httpMethod())
                .headers(signed.headers())
                .objectKey(objectKey)
                .publicUrl(publicUrl)
                .expiresAt(signed.expiresAt())
                .build();
    }

    @Transactional
    public CommitProductImageResult commitUpload(UUID productId, CommitProductImageCommand command) {
        Product product = findProductOrThrow(productId);
        if (product.isDeleted()) {
            throw new ProductDomainException("Cannot attach images to a deleted product.");
        }
        validateContent(command.getContentType(), command.getFileSize());
        validateRole(command.getRole());
        validateObjectKeyBelongsToProduct(productId, command.getObjectKey());

        Optional<ProductImagePersistencePort.ProductImageRow> existing = productImagePersistence.findByStorageKeyAndProductId(
                command.getObjectKey(),
                productId
        );
        if (existing.isPresent()) {
            return toIdempotentCommitResult(product, command, existing.get());
        }

        if (command.getRole() == ProductImageRole.GALLERY) {
            long galleryCount = productImagePersistence.findAllActiveByProductId(productId).stream()
                    .filter(row -> row.role() == ProductImageRole.GALLERY)
                    .count();
            if (galleryCount >= MAX_GALLERY_IMAGES) {
                throw new ProductDomainException("Maximum number of gallery images reached: " + MAX_GALLERY_IMAGES);
            }
        }

        String publicUrl = productImageStorage.publicUrlForKey(command.getObjectKey());

        if (command.getRole() == ProductImageRole.MAIN) {
            productImagePersistence.demoteActiveMainsToGallery(productId);
        }

        UUID imageId;
        try {
            imageId = productImagePersistence.insert(
                    productId,
                    command.getObjectKey(),
                    publicUrl,
                    command.getRole(),
                    command.getSortOrder(),
                    command.getContentType(),
                    command.getFileSize()
            );
        } catch (RuntimeException ex) {
            // Retry-safe idempotency for a race where another request inserted the same storage key first.
            Optional<ProductImagePersistencePort.ProductImageRow> raced = productImagePersistence.findByStorageKeyAndProductId(
                    command.getObjectKey(),
                    productId
            );
            if (raced.isPresent()) {
                return toIdempotentCommitResult(product, command, raced.get());
            }
            throw ex;
        }

        if (command.getRole() == ProductImageRole.MAIN) {
            product.changeMainImageUrl(publicUrl);
            productRepository.update(product);
            variantMainImageSyncPort.syncAllForProduct(productId);
        }

        return CommitProductImageResult.builder()
                .id(imageId)
                .publicUrl(publicUrl)
                .role(command.getRole().name())
                .sortOrder(command.getSortOrder())
                .build();
    }

    private CommitProductImageResult toIdempotentCommitResult(
            Product product,
            CommitProductImageCommand command,
            ProductImagePersistencePort.ProductImageRow existing
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

        if (existing.role() == ProductImageRole.MAIN) {
            product.changeMainImageUrl(existing.publicUrl());
            productRepository.update(product);
        }

        return CommitProductImageResult.builder()
                .id(existing.id())
                .publicUrl(existing.publicUrl())
                .role(existing.role().name())
                .sortOrder(existing.sortOrder())
                .build();
    }

    @Transactional
    public void deleteImage(UUID productId, UUID imageId) {
        executeWithMainImagePolicy(productId, "delete images", product -> {
            productImagePersistence.findActiveByIdAndProduct(imageId, productId)
                    .orElseThrow(() -> new ProductDomainException("Product image not found."));

            int deleted = productImagePersistence.softDelete(imageId, productId);
            if (deleted == 0) {
                throw new ProductDomainException("Product image not found or already deleted.");
            }
            return null;
        });
    }

    @Transactional
    public ReorderProductImagesResult reorderImages(UUID productId, ReorderProductImagesCommand command) {
        return executeWithMainImagePolicy(productId, "reorder images", product -> {
            if (command == null || command.getOrderedImageIds() == null) {
                throw new ProductDomainException("orderedImageIds is required.");
            }
            List<UUID> requested = command.getOrderedImageIds();
            List<ProductImagePersistencePort.ProductImageRow> activeRows = productImagePersistence.findAllActiveByProductId(productId);
            List<UUID> activeIds = activeRows.stream().map(ProductImagePersistencePort.ProductImageRow::id).toList();

            if (requested.size() != new HashSet<>(requested).size()) {
                throw new ProductDomainException("orderedImageIds contains duplicates.");
            }
            if (requested.size() != activeIds.size()) {
                throw new ProductDomainException("orderedImageIds must include all active product images.");
            }
            if (!new HashSet<>(requested).equals(new HashSet<>(activeIds))) {
                throw new ProductDomainException("orderedImageIds must match active product images exactly.");
            }

            for (int i = 0; i < requested.size(); i++) {
                UUID imageId = requested.get(i);
                int updated = productImagePersistence.updateSortOrder(imageId, productId, i);
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

    private <T> T executeWithMainImagePolicy(UUID productId, String actionName, ImageMutation<T> mutation) {
        Product product = findProductOrThrow(productId);
        if (product.isDeleted()) {
            throw new ProductDomainException("Cannot " + actionName + " of a deleted product.");
        }
        T result = mutation.apply(product);
        applyFirstActiveImageAsMainPolicy(productId, product);
        return result;
    }

    /**
     * Shared post-action policy:
     * after delete/reorder, the first active image by sortOrder is always MAIN.
     */
    private void applyFirstActiveImageAsMainPolicy(UUID productId, Product product) {
        List<ProductImagePersistencePort.ProductImageRow> activeRows = productImagePersistence.findAllActiveByProductId(productId)
                .stream()
                .sorted(Comparator.comparingInt(ProductImagePersistencePort.ProductImageRow::sortOrder))
                .toList();

        if (activeRows.isEmpty()) {
            product.changeMainImageUrl(DEFAULT_PRODUCT_IMAGE_URL);
            productRepository.update(product);
            variantMainImageSyncPort.syncAllForProduct(productId);
            return;
        }

        ProductImagePersistencePort.ProductImageRow first = activeRows.getFirst();
        productImagePersistence.demoteActiveMainsToGallery(productId);
        productImagePersistence.updateRole(first.id(), productId, ProductImageRole.MAIN);
        product.changeMainImageUrl(first.publicUrl());
        productRepository.update(product);
        variantMainImageSyncPort.syncAllForProduct(productId);
    }

    @FunctionalInterface
    private interface ImageMutation<T> {
        T apply(Product product);
    }

    private Product findProductOrThrow(UUID productId) {
        return productRepository.findById(new ProductId(productId))
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
    }

    private void validateProductWritable(UUID productId) {
        Product product = findProductOrThrow(productId);
        if (product.isDeleted()) {
            throw new ProductDomainException("Cannot upload images for a deleted product.");
        }
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

    private void validateObjectKeyBelongsToProduct(UUID productId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new ProductDomainException("Object key is required.");
        }
        String expectedPrefix = "products/" + productId + "/";
        if (!objectKey.startsWith(expectedPrefix)) {
            throw new ProductDomainException("Object key does not belong to this product.");
        }
    }

    private String buildObjectKey(UUID productId, String safeFileName) {
        YearMonth ym = YearMonth.now(ZoneOffset.UTC);
        String ymPart = ym.getYear() + "/" + String.format("%02d", ym.getMonthValue());
        return "products/" + productId + "/" + ymPart + "/" + UUID.randomUUID() + "-" + safeFileName;
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
            ext = base.substring(dot); // ex: ".jpg"
            base = base.substring(0, dot);
        }

        // 한글, 공백 등은 URL Safe 하게 변환
        base = base.replaceAll("[^a-zA-Z0-9_-]", "");

        if (base.isEmpty()) {
            base = "upload";
        }

        // 길이 제한
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }

        return base + ext;
    }
}
