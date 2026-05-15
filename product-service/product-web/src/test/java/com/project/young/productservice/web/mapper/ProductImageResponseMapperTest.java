package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.result.CommitProductImageResult;
import com.project.young.productservice.application.dto.result.PresignProductImageUploadResult;
import com.project.young.productservice.application.dto.result.ReorderProductImagesResult;
import com.project.young.productservice.web.dto.ProductImageCommitResponse;
import com.project.young.productservice.web.dto.ProductImagePresignResponse;
import com.project.young.productservice.web.dto.ProductImageReorderResponse;
import com.project.young.productservice.web.message.ProductImageResponseMessageFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductImageResponseMapperTest {

    private ProductImageResponseMapper productImageResponseMapper;

    @BeforeEach
    void setUp() {
        productImageResponseMapper = new ProductImageResponseMapper(new ProductImageResponseMessageFactory());
    }

    @Test
    @DisplayName("presign 결과를 응답 DTO로 매핑한다")
    void toProductImagePresignResponse() {
        PresignProductImageUploadResult result = PresignProductImageUploadResult.builder()
                .uploadUrl("https://upload.example")
                .httpMethod("PUT")
                .headers(Map.of("Content-Type", "image/png"))
                .objectKey("products/1/a.png")
                .publicUrl("https://cdn.example/a.png")
                .expiresAt(Instant.parse("2026-05-13T00:00:00Z"))
                .build();

        ProductImagePresignResponse response = productImageResponseMapper.toProductImagePresignResponse(result);

        assertThat(response.uploadUrl()).isEqualTo("https://upload.example");
        assertThat(response.objectKey()).isEqualTo("products/1/a.png");
    }

    @Test
    @DisplayName("commit 결과를 응답 DTO로 매핑하고 메시지를 채운다")
    void toProductImageCommitResponse() {
        UUID imageId = UUID.randomUUID();
        CommitProductImageResult result = CommitProductImageResult.builder()
                .id(imageId)
                .publicUrl("https://cdn.example/a.png")
                .role("MAIN")
                .sortOrder(0)
                .build();

        ProductImageCommitResponse response = productImageResponseMapper.toProductImageCommitResponse(result);

        assertThat(response.id()).isEqualTo(imageId);
        assertThat(response.role()).isEqualTo("MAIN");
        assertThat(response.message()).containsIgnoringCase("committed");
    }

    @Test
    @DisplayName("reorder 결과를 응답 DTO로 매핑하고 메시지를 채운다")
    void toProductImageReorderResponse() {
        UUID productId = UUID.randomUUID();
        List<UUID> orderedIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        ReorderProductImagesResult result = ReorderProductImagesResult.builder()
                .productId(productId)
                .reorderedCount(2)
                .orderedImageIds(orderedIds)
                .build();

        ProductImageReorderResponse response = productImageResponseMapper.toProductImageReorderResponse(result);

        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.reorderedCount()).isEqualTo(2);
        assertThat(response.orderedImageIds()).isEqualTo(orderedIds);
        assertThat(response.message()).containsIgnoringCase("reordered");
    }
}
