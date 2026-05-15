package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.result.CommitProductImageResult;
import com.project.young.productservice.application.dto.result.PresignProductImageUploadResult;
import com.project.young.productservice.application.dto.result.ReorderProductImagesResult;
import com.project.young.productservice.application.service.ProductImageApplicationService;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.dto.ProductImageCommitRequest;
import com.project.young.productservice.web.dto.ProductImageCommitResponse;
import com.project.young.productservice.web.dto.ProductImagePresignRequest;
import com.project.young.productservice.web.dto.ProductImagePresignResponse;
import com.project.young.productservice.web.dto.ProductImageReorderRequest;
import com.project.young.productservice.web.dto.ProductImageReorderResponse;
import com.project.young.productservice.web.mapper.ProductImageResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProductImageController.class)
@Import({SecurityConfig.class, TestConfig.class})
class AdminProductImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductImageApplicationService productImageApplicationService;

    @MockitoBean
    private ProductImageResponseMapper productImageResponseMapper;

    @Nested
    class PresignTests {

        @Test
        @DisplayName("ADMIN presign-upload 200")
        @WithMockUser(authorities = "ADMIN")
        void presign_ok() throws Exception {
            UUID productId = UUID.randomUUID();
            PresignProductImageUploadResult result = PresignProductImageUploadResult.builder()
                    .uploadUrl("https://example.com/put")
                    .httpMethod("PUT")
                    .headers(Map.of("Content-Type", "image/jpeg"))
                    .objectKey("products/" + productId + "/2026/01/x.jpg")
                    .publicUrl("https://pub/x.jpg")
                    .expiresAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            ProductImagePresignResponse response = ProductImagePresignResponse.builder()
                    .uploadUrl(result.uploadUrl())
                    .httpMethod(result.httpMethod())
                    .headers(result.headers())
                    .objectKey(result.objectKey())
                    .publicUrl(result.publicUrl())
                    .expiresAt(result.expiresAt())
                    .build();

            when(productImageApplicationService.presignUpload(eq(productId), any())).thenReturn(result);
            when(productImageResponseMapper.toProductImagePresignResponse(result)).thenReturn(response);

            ProductImagePresignRequest body = ProductImagePresignRequest.builder()
                    .fileName("a.jpg")
                    .contentType("image/jpeg")
                    .contentLength(1024L)
                    .role("MAIN")
                    .sortOrder(0)
                    .build();

            mockMvc.perform(post("/admin/products/{productId}/images/presign-upload", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.objectKey").exists())
                    .andExpect(jsonPath("$.uploadUrl").value("https://example.com/put"));

            verify(productImageApplicationService).presignUpload(eq(productId), any());
            verify(productImageResponseMapper).toProductImagePresignResponse(result);
        }
    }

    @Nested
    class CommitTests {

        @Test
        @DisplayName("ADMIN commit 201")
        @WithMockUser(authorities = "ADMIN")
        void commit_created() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID imageId = UUID.randomUUID();
            CommitProductImageResult result = CommitProductImageResult.builder()
                    .id(imageId)
                    .publicUrl("https://pub/x.jpg")
                    .role("MAIN")
                    .sortOrder(0)
                    .build();
            ProductImageCommitResponse response = ProductImageCommitResponse.builder()
                    .id(imageId)
                    .publicUrl("https://pub/x.jpg")
                    .role("MAIN")
                    .sortOrder(0)
                    .message("Image committed successfully")
                    .build();

            when(productImageApplicationService.commitUpload(eq(productId), any())).thenReturn(result);
            when(productImageResponseMapper.toProductImageCommitResponse(result)).thenReturn(response);

            ProductImageCommitRequest body = ProductImageCommitRequest.builder()
                    .objectKey("products/" + productId + "/2026/01/x.jpg")
                    .contentType("image/jpeg")
                    .fileSize(1024L)
                    .role("MAIN")
                    .sortOrder(0)
                    .build();

            mockMvc.perform(post("/admin/products/{productId}/images/commit", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(imageId.toString()))
                    .andExpect(jsonPath("$.role").value("MAIN"));

            verify(productImageApplicationService).commitUpload(eq(productId), any());
            verify(productImageResponseMapper).toProductImageCommitResponse(result);
        }
    }

    @Nested
    class DeleteTests {

        @Test
        @DisplayName("ADMIN delete image 204")
        @WithMockUser(authorities = "ADMIN")
        void delete_noContent() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID imageId = UUID.randomUUID();

            mockMvc.perform(delete("/admin/products/{productId}/images/{imageId}", productId, imageId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            verify(productImageApplicationService).deleteImage(productId, imageId);
        }
    }

    @Nested
    class ReorderTests {

        @Test
        @DisplayName("ADMIN reorder images 200")
        @WithMockUser(authorities = "ADMIN")
        void reorder_ok() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID image1 = UUID.randomUUID();
            UUID image2 = UUID.randomUUID();
            ReorderProductImagesResult result = ReorderProductImagesResult.builder()
                    .productId(productId)
                    .reorderedCount(2)
                    .orderedImageIds(List.of(image2, image1))
                    .build();
            ProductImageReorderResponse response = ProductImageReorderResponse.builder()
                    .productId(productId)
                    .reorderedCount(2)
                    .orderedImageIds(List.of(image2, image1))
                    .message("Images reordered successfully")
                    .build();

            when(productImageApplicationService.reorderImages(eq(productId), any())).thenReturn(result);
            when(productImageResponseMapper.toProductImageReorderResponse(result)).thenReturn(response);

            ProductImageReorderRequest body = ProductImageReorderRequest.builder()
                    .orderedImageIds(List.of(image2, image1))
                    .build();

            mockMvc.perform(patch("/admin/products/{productId}/images/reorder", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.reorderedCount").value(2));

            verify(productImageApplicationService).reorderImages(eq(productId), any());
            verify(productImageResponseMapper).toProductImageReorderResponse(result);
        }
    }
}
