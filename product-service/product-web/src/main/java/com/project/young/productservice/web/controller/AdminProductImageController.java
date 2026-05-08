package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.command.CommitProductImageCommand;
import com.project.young.productservice.application.dto.command.PresignProductImageUploadCommand;
import com.project.young.productservice.application.dto.result.CommitProductImageResult;
import com.project.young.productservice.application.dto.result.PresignProductImageUploadResult;
import com.project.young.productservice.application.service.ProductImageApplicationService;
import com.project.young.productservice.domain.valueobject.ProductImageRole;
import com.project.young.productservice.web.dto.ProductImageCommitRequest;
import com.project.young.productservice.web.dto.ProductImageCommitResponse;
import com.project.young.productservice.web.dto.ProductImagePresignRequest;
import com.project.young.productservice.web.dto.ProductImagePresignResponse;
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
@RequestMapping("admin/products/{productId}/images")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminProductImageController {

    private final ProductImageApplicationService productImageApplicationService;

    public AdminProductImageController(ProductImageApplicationService productImageApplicationService) {
        this.productImageApplicationService = productImageApplicationService;
    }

    @PostMapping("/presign-upload")
    public ResponseEntity<ProductImagePresignResponse> presignUpload(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody ProductImagePresignRequest request
    ) {
        log.info("REST presign-upload productId={}", productId);
        PresignProductImageUploadCommand command = PresignProductImageUploadCommand.builder()
                .fileName(request.getFileName())
                .contentType(request.getContentType())
                .contentLength(request.getContentLength())
                .role(parseRole(request.getRole()))
                .sortOrder(request.getSortOrder())
                .build();

        PresignProductImageUploadResult result = productImageApplicationService.presignUpload(productId, command);
        return ResponseEntity.ok(ProductImagePresignResponse.builder()
                .uploadUrl(result.uploadUrl())
                .httpMethod(result.httpMethod())
                .headers(result.headers())
                .objectKey(result.objectKey())
                .publicUrl(result.publicUrl())
                .expiresAt(result.expiresAt())
                .build());
    }

    @PostMapping("/commit")
    public ResponseEntity<ProductImageCommitResponse> commit(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody ProductImageCommitRequest request
    ) {
        log.info("REST commit image productId={}", productId);
        CommitProductImageCommand command = CommitProductImageCommand.builder()
                .objectKey(request.getObjectKey())
                .contentType(request.getContentType())
                .fileSize(request.getFileSize())
                .role(parseRole(request.getRole()))
                .sortOrder(request.getSortOrder())
                .build();

        CommitProductImageResult result = productImageApplicationService.commitUpload(productId, command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductImageCommitResponse.builder()
                .id(result.id())
                .publicUrl(result.publicUrl())
                .role(result.role())
                .sortOrder(result.sortOrder())
                .message("Image committed.")
                .build());
    }

    @PatchMapping("/{imageId}/set-main")
    public ResponseEntity<ProductImageCommitResponse> setMain(
            @PathVariable("productId") UUID productId,
            @PathVariable("imageId") UUID imageId
    ) {
        log.info("REST set-main productId={}, imageId={}", productId, imageId);
        CommitProductImageResult result = productImageApplicationService.setMainImage(productId, imageId);
        return ResponseEntity.ok(ProductImageCommitResponse.builder()
                .id(result.id())
                .publicUrl(result.publicUrl())
                .role(result.role())
                .sortOrder(result.sortOrder())
                .message("Main image updated.")
                .build());
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(
            @PathVariable("productId") UUID productId,
            @PathVariable("imageId") UUID imageId
    ) {
        log.info("REST delete image productId={}, imageId={}", productId, imageId);
        productImageApplicationService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
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
