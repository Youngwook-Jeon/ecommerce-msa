package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.result.CommitProductImageResult;
import com.project.young.productservice.application.dto.result.PresignProductImageUploadResult;
import com.project.young.productservice.application.dto.result.ReorderProductImagesResult;
import com.project.young.productservice.web.dto.ProductImageCommitResponse;
import com.project.young.productservice.web.dto.ProductImagePresignResponse;
import com.project.young.productservice.web.dto.ProductImageReorderResponse;
import com.project.young.productservice.web.message.ProductImageResponseMessageFactory;
import org.springframework.stereotype.Component;

@Component
public class ProductImageResponseMapper {

    private final ProductImageResponseMessageFactory messageFactory;

    public ProductImageResponseMapper(ProductImageResponseMessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    public ProductImagePresignResponse toProductImagePresignResponse(PresignProductImageUploadResult result) {
        return ProductImagePresignResponse.builder()
                .uploadUrl(result.uploadUrl())
                .httpMethod(result.httpMethod())
                .headers(result.headers())
                .objectKey(result.objectKey())
                .publicUrl(result.publicUrl())
                .expiresAt(result.expiresAt())
                .build();
    }

    public ProductImageCommitResponse toProductImageCommitResponse(CommitProductImageResult result) {
        return ProductImageCommitResponse.builder()
                .id(result.id())
                .publicUrl(result.publicUrl())
                .role(result.role())
                .sortOrder(result.sortOrder())
                .message(messageFactory.imageCommitted())
                .build();
    }

    public ProductImageReorderResponse toProductImageReorderResponse(ReorderProductImagesResult result) {
        return ProductImageReorderResponse.builder()
                .productId(result.productId())
                .reorderedCount(result.reorderedCount())
                .orderedImageIds(result.orderedImageIds())
                .message(messageFactory.imagesReordered())
                .build();
    }
}
