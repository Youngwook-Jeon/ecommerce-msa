package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.command.CommitProductImageCommand;
import com.project.young.productservice.application.dto.command.PresignProductImageUploadCommand;
import com.project.young.productservice.application.dto.command.ReorderProductImagesCommand;
import com.project.young.productservice.application.service.ProductOptionValueImageApplicationService;
import com.project.young.productservice.domain.valueobject.ProductImageRole;
import com.project.young.productservice.web.dto.ProductImageCommitRequest;
import com.project.young.productservice.web.dto.ProductImageCommitResponse;
import com.project.young.productservice.web.dto.ProductImagePresignRequest;
import com.project.young.productservice.web.dto.ProductImagePresignResponse;
import com.project.young.productservice.web.dto.ProductImageReorderRequest;
import com.project.young.productservice.web.dto.ProductImageReorderResponse;
import com.project.young.productservice.web.mapper.ProductImageResponseMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("admin/products/{productId}/option-values/{productOptionValueId}/images")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminProductOptionValueImageController {

    private final ProductOptionValueImageApplicationService productOptionValueImageApplicationService;
    private final ProductImageResponseMapper productImageResponseMapper;

    public AdminProductOptionValueImageController(
            ProductOptionValueImageApplicationService productOptionValueImageApplicationService,
            ProductImageResponseMapper productImageResponseMapper
    ) {
        this.productOptionValueImageApplicationService = productOptionValueImageApplicationService;
        this.productImageResponseMapper = productImageResponseMapper;
    }

    @PostMapping("/presign-upload")
    public ResponseEntity<ProductImagePresignResponse> presignUpload(
            @PathVariable("productId") UUID productId,
            @PathVariable("productOptionValueId") UUID productOptionValueId,
            @Valid @RequestBody ProductImagePresignRequest request
    ) {
        log.info("REST presign-upload option value image productId={}, povId={}", productId, productOptionValueId);
        PresignProductImageUploadCommand command = PresignProductImageUploadCommand.builder()
                .fileName(request.getFileName())
                .contentType(request.getContentType())
                .contentLength(request.getContentLength())
                .role(parseRole(request.getRole()))
                .sortOrder(request.getSortOrder())
                .build();

        return ResponseEntity.ok(productImageResponseMapper.toProductImagePresignResponse(
                productOptionValueImageApplicationService.presignUpload(productId, productOptionValueId, command)
        ));
    }

    @PostMapping("/commit")
    public ResponseEntity<ProductImageCommitResponse> commit(
            @PathVariable("productId") UUID productId,
            @PathVariable("productOptionValueId") UUID productOptionValueId,
            @Valid @RequestBody ProductImageCommitRequest request
    ) {
        log.info("REST commit option value image productId={}, povId={}", productId, productOptionValueId);
        CommitProductImageCommand command = CommitProductImageCommand.builder()
                .objectKey(request.getObjectKey())
                .contentType(request.getContentType())
                .fileSize(request.getFileSize())
                .role(parseRole(request.getRole()))
                .sortOrder(request.getSortOrder())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(
                productImageResponseMapper.toProductImageCommitResponse(
                        productOptionValueImageApplicationService.commitUpload(
                                productId,
                                productOptionValueId,
                                command
                        )
                )
        );
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(
            @PathVariable("productId") UUID productId,
            @PathVariable("productOptionValueId") UUID productOptionValueId,
            @PathVariable("imageId") UUID imageId
    ) {
        log.info("REST delete option value image productId={}, povId={}, imageId={}", productId, productOptionValueId, imageId);
        productOptionValueImageApplicationService.deleteImage(productId, productOptionValueId, imageId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/reorder")
    public ResponseEntity<ProductImageReorderResponse> reorder(
            @PathVariable("productId") UUID productId,
            @PathVariable("productOptionValueId") UUID productOptionValueId,
            @Valid @RequestBody ProductImageReorderRequest request
    ) {
        log.info("REST reorder option value images productId={}, povId={}", productId, productOptionValueId);
        ReorderProductImagesCommand command = ReorderProductImagesCommand.builder()
                .orderedImageIds(request.getOrderedImageIds())
                .build();

        return ResponseEntity.ok(productImageResponseMapper.toProductImageReorderResponse(
                productOptionValueImageApplicationService.reorderImages(productId, productOptionValueId, command)
        ));
    }

    private static ProductImageRole parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        try {
            return ProductImageRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid role: " + raw);
        }
    }
}
