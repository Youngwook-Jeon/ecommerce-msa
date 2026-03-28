package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.command.CreateProductCommand;
import com.project.young.productservice.application.dto.result.CreateProductResult;
import com.project.young.productservice.application.dto.result.DeleteProductResult;
import com.project.young.productservice.application.dto.command.UpdateProductCommand;
import com.project.young.productservice.application.dto.command.UpdateProductStatusCommand;
import com.project.young.productservice.application.dto.result.UpdateProductResult;
import com.project.young.productservice.application.service.ProductApplicationService;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.dto.CreateProductResponse;
import com.project.young.productservice.web.dto.DeleteProductResponse;
import com.project.young.productservice.web.dto.UpdateProductResponse;
import com.project.young.productservice.web.mapper.ProductResponseMapper;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, TestConfig.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductApplicationService productApplicationService;

    @MockitoBean
    private ProductResponseMapper productResponseMapper;

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("유효한 요청 시 201 CREATED와 CreateProductResponse 반환")
        @WithMockUser(authorities = "ADMIN")
        void create_ValidRequest_Returns201AndResponse() throws Exception {
            // Given
            CreateProductCommand command = CreateProductCommand.builder()
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("99000"))
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .categoryId(1L)
                    .conditionType(ConditionType.NEW)
                    .build();

            UUID productId = UUID.randomUUID();
            CreateProductResult serviceResult = new CreateProductResult(productId, "와이드핏 데님");
            CreateProductResponse expectedResponse = CreateProductResponse.builder()
                    .id(productId)
                    .name("와이드핏 데님")
                    .message("Product created successfully.")
                    .build();

            when(productApplicationService.createProduct(any(CreateProductCommand.class)))
                    .thenReturn(serviceResult);
            when(productResponseMapper.toCreateProductResponse(serviceResult))
                    .thenReturn(expectedResponse);

            // When & Then
            mockMvc.perform(post("/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("와이드핏 데님"))
                    .andExpect(jsonPath("$.message").exists());

            verify(productApplicationService).createProduct(any(CreateProductCommand.class));
            verify(productResponseMapper).toCreateProductResponse(serviceResult);
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void create_WithoutAdminAuthority_Returns403() throws Exception {
            CreateProductCommand command = CreateProductCommand.builder()
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("99000"))
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .categoryId(1L)
                    .conditionType(ConditionType.NEW)
                    .build();

            mockMvc.perform(post("/products")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isForbidden());

            verify(productApplicationService, never()).createProduct(any());
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("유효한 요청 시 200 OK와 UpdateProductResponse 반환")
        @WithMockUser(authorities = "ADMIN")
        void update_ValidRequest_Returns200AndResponse() throws Exception {
            // Given
            UUID productId = UUID.randomUUID();
            UpdateProductCommand command = UpdateProductCommand.builder()
                    .name("와이드핏 데님 수정")
                    .description("수정된 와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("89000"))
                    .brand("브랜드B")
                    .mainImageUrl("https://example.com/updated.jpg")
                    .categoryId(2L)
                    .build();

            UpdateProductResult serviceResult = new UpdateProductResult(
                    productId,
                    "와이드핏 데님 수정",
                    2L,
                    "수정된 와이드핏 데님 상세 설명입니다. 20자 이상.",
                    "브랜드B",
                    "https://example.com/updated.jpg",
                    new BigDecimal("89000"),
                    ProductStatus.DRAFT,
                    ConditionType.USED
            );

            UpdateProductResponse expectedResponse = UpdateProductResponse.builder()
                    .id(productId)
                    .name("와이드핏 데님 수정")
                    .categoryId(2L)
                    .description("수정된 와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .brand("브랜드B")
                    .mainImageUrl("https://example.com/updated.jpg")
                    .basePrice(new BigDecimal("89000"))
                    .status("DRAFT")
                    .conditionType("USED_GOOD")
                    .message("Product updated successfully.")
                    .build();

            when(productApplicationService.updateProduct(eq(productId), any(UpdateProductCommand.class)))
                    .thenReturn(serviceResult);
            when(productResponseMapper.toUpdateProductResponse(serviceResult))
                    .thenReturn(expectedResponse);

            // When & Then
            mockMvc.perform(put("/products/{productId}", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("와이드핏 데님 수정"))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.message").exists());

            verify(productApplicationService).updateProduct(eq(productId), any(UpdateProductCommand.class));
            verify(productResponseMapper).toUpdateProductResponse(serviceResult);
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void update_WithoutAdminAuthority_Returns403() throws Exception {
            UUID productId = UUID.randomUUID();
            UpdateProductCommand command = UpdateProductCommand.builder()
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다. 20자 이상.")
                    .basePrice(new BigDecimal("99000"))
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .categoryId(1L)
                    .build();

            mockMvc.perform(put("/products/{productId}", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isForbidden());

            verify(productApplicationService, never()).updateProduct(any(), any());
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatusTests {

        @Test
        @DisplayName("유효한 요청 시 200 OK와 status 업데이트 응답")
        @WithMockUser(authorities = "ADMIN")
        void updateStatus_ValidRequest_Returns200AndResponse() throws Exception {
            UUID productId = UUID.randomUUID();
            UpdateProductStatusCommand command = UpdateProductStatusCommand.builder()
                    .status(ProductStatus.ACTIVE)
                    .build();

            UpdateProductResult serviceResult = new UpdateProductResult(
                    productId,
                    "상품",
                    1L,
                    "상품에 대한 설명입니다. 20자 이상 충분히 깁니다.",
                    "브랜드",
                    "https://example.com/img.jpg",
                    new BigDecimal("10000"),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW
            );

            UpdateProductResponse expectedResponse = UpdateProductResponse.builder()
                    .id(productId)
                    .name("상품")
                    .categoryId(1L)
                    .description("상품에 대한 설명입니다. 20자 이상 충분히 깁니다.")
                    .brand("브랜드")
                    .mainImageUrl("https://example.com/img.jpg")
                    .basePrice(new BigDecimal("10000"))
                    .status("ACTIVE")
                    .conditionType("NEW")
                    .message("Product status updated successfully")
                    .build();

            when(productApplicationService.updateProductStatus(eq(productId), any(UpdateProductStatusCommand.class)))
                    .thenReturn(serviceResult);
            when(productResponseMapper.toUpdateProductStatusResponse(serviceResult))
                    .thenReturn(expectedResponse);

            mockMvc.perform(patch("/products/{productId}/status", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.message").value("Product status updated successfully"));

            verify(productApplicationService).updateProductStatus(eq(productId), any(UpdateProductStatusCommand.class));
            verify(productResponseMapper).toUpdateProductStatusResponse(serviceResult);
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void updateStatus_WithoutAdminAuthority_Returns403() throws Exception {
            UUID productId = UUID.randomUUID();
            UpdateProductStatusCommand command = UpdateProductStatusCommand.builder()
                    .status(ProductStatus.INACTIVE)
                    .build();

            mockMvc.perform(patch("/products/{productId}/status", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isForbidden());

            verify(productApplicationService, never()).updateProductStatus(any(), any());
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("유효한 productId로 삭제 시 200 OK와 DeleteProductResponse 반환")
        @WithMockUser(authorities = "ADMIN")
        void delete_ValidProductId_Returns200AndResponse() throws Exception {
            // Given
            UUID productId = UUID.randomUUID();
            DeleteProductResult serviceResult = new DeleteProductResult(productId, "와이드핏 데님");
            DeleteProductResponse expectedResponse = DeleteProductResponse.builder()
                    .id(productId)
                    .name("와이드핏 데님")
                    .message("Product deleted successfully.")
                    .build();

            when(productApplicationService.deleteProduct(productId)).thenReturn(serviceResult);
            when(productResponseMapper.toDeleteProductResponse(serviceResult))
                    .thenReturn(expectedResponse);

            // When & Then
            mockMvc.perform(delete("/products/{productId}", productId)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("와이드핏 데님"))
                    .andExpect(jsonPath("$.message").exists());

            verify(productApplicationService).deleteProduct(productId);
            verify(productResponseMapper).toDeleteProductResponse(serviceResult);
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void delete_WithoutAdminAuthority_Returns403() throws Exception {
            UUID productId = UUID.randomUUID();

            mockMvc.perform(delete("/products/{productId}", productId)
                            .with(csrf()))
                    .andExpect(status().isForbidden());

            verify(productApplicationService, never()).deleteProduct(any());
        }
    }
}

