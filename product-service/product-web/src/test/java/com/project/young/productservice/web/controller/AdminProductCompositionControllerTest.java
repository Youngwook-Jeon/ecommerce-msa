package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.command.AddProductOptionGroupCommand;
import com.project.young.productservice.application.dto.command.AddProductOptionValueCommand;
import com.project.young.productservice.application.dto.command.AddProductOptionValuesCommand;
import com.project.young.productservice.application.dto.command.AddProductVariantCommand;
import com.project.young.productservice.application.dto.command.AddProductVariantsCommand;
import com.project.young.productservice.application.dto.command.ReorderProductOptionGroupsCommand;
import com.project.young.productservice.application.dto.command.UpdateProductVariantCommand;
import com.project.young.productservice.application.dto.result.AddProductOptionGroupResult;
import com.project.young.productservice.application.dto.result.AddProductOptionValueToGroupResult;
import com.project.young.productservice.application.dto.result.AddProductVariantResult;
import com.project.young.productservice.application.dto.result.DeleteProductOptionGroupResult;
import com.project.young.productservice.application.dto.result.DeleteProductOptionValueResult;
import com.project.young.productservice.application.dto.result.DeleteProductVariantResult;
import com.project.young.productservice.application.dto.result.ReorderProductOptionGroupsResult;
import com.project.young.productservice.application.dto.result.UpdateProductVariantResult;
import com.project.young.productservice.application.service.ProductApplicationService;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.dto.AddProductOptionGroupResponse;
import com.project.young.productservice.web.dto.AddProductOptionValueToGroupResponse;
import com.project.young.productservice.web.dto.AddProductVariantResponse;
import com.project.young.productservice.web.dto.DeleteProductOptionGroupResponse;
import com.project.young.productservice.web.dto.DeleteProductOptionValueResponse;
import com.project.young.productservice.web.dto.DeleteProductVariantResponse;
import com.project.young.productservice.web.dto.ReorderProductOptionGroupsResponse;
import com.project.young.productservice.web.dto.UpdateProductVariantResponse;
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
import java.util.List;
import java.util.Set;
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

@WebMvcTest(AdminProductCompositionController.class)
@Import({SecurityConfig.class, TestConfig.class})
class AdminProductCompositionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductApplicationService productApplicationService;

    @MockitoBean
    private ProductResponseMapper productResponseMapper;

    @Nested
    @DisplayName("POST /admin/products/{productId}/option-groups")
    class AddOptionGroupTests {

        @Test
        @DisplayName("ADMIN이면 201과 본문 반환")
        @WithMockUser(authorities = "ADMIN")
        void withAdmin_Returns201() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID pogId = UUID.randomUUID();
            UUID globalOgId = UUID.randomUUID();

            UUID globalValueId = UUID.randomUUID();
            AddProductOptionGroupCommand command = AddProductOptionGroupCommand.builder()
                    .optionGroupId(globalOgId)
                    .stepOrder(1.0d)
                    .required(true)
                    .optionValues(List.of(
                            AddProductOptionValueCommand.builder()
                                    .optionValueId(globalValueId)
                                    .priceDelta(BigDecimal.ZERO)
                                    .isDefault(true)
                                    .isActive(true)
                                    .build()
                    ))
                    .build();

            AddProductOptionGroupResult result = AddProductOptionGroupResult.builder()
                    .productId(productId)
                    .productOptionGroupId(pogId)
                    .optionGroupId(globalOgId)
                    .stepOrder(1.0d)
                    .required(true)
                    .optionValueCount(1)
                    .build();

            AddProductOptionGroupResponse response = AddProductOptionGroupResponse.builder()
                    .productId(productId)
                    .productOptionGroupId(pogId)
                    .optionGroupId(globalOgId)
                    .stepOrder(1.0d)
                    .required(true)
                    .optionValueCount(1)
                    .message("ok")
                    .build();

            when(productApplicationService.addProductOptionGroup(eq(productId), any(AddProductOptionGroupCommand.class)))
                    .thenReturn(result);
            when(productResponseMapper.toAddProductOptionGroupResponse(result)).thenReturn(response);

            mockMvc.perform(post("/admin/products/{productId}/option-groups", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.productOptionGroupId").value(pogId.toString()))
                    .andExpect(jsonPath("$.optionValueCount").value(1));

            verify(productApplicationService).addProductOptionGroup(eq(productId), any(AddProductOptionGroupCommand.class));
        }
    }

    @Nested
    @DisplayName("POST /admin/products/{productId}/option-groups/{productOptionGroupId}/option-values")
    class AddOptionValueTests {

        @Test
        @DisplayName("ADMIN이면 201과 본문 반환")
        @WithMockUser(authorities = "ADMIN")
        void withAdmin_Returns201() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID pogId = UUID.randomUUID();
            UUID povId = UUID.randomUUID();
            UUID globalValId = UUID.randomUUID();

            AddProductOptionValueCommand value1 = AddProductOptionValueCommand.builder()
                    .optionValueId(globalValId)
                    .priceDelta(BigDecimal.ZERO)
                    .isDefault(true)
                    .isActive(true)
                    .build();
            AddProductOptionValueCommand value2 = AddProductOptionValueCommand.builder()
                    .optionValueId(UUID.randomUUID())
                    .priceDelta(new BigDecimal("1000"))
                    .isDefault(false)
                    .isActive(true)
                    .build();
            AddProductOptionValuesCommand command = AddProductOptionValuesCommand.builder()
                    .optionValues(List.of(value1, value2))
                    .build();

            AddProductOptionValueToGroupResult result = AddProductOptionValueToGroupResult.builder()
                    .productId(productId)
                    .productOptionGroupId(pogId)
                    .productOptionValueId(povId)
                    .optionValueId(globalValId)
                    .priceDelta(BigDecimal.ZERO)
                    .build();

            AddProductOptionValueToGroupResponse response = AddProductOptionValueToGroupResponse.builder()
                    .productId(productId)
                    .productOptionGroupId(pogId)
                    .productOptionValueId(povId)
                    .optionValueId(globalValId)
                    .priceDelta(BigDecimal.ZERO)
                    .message("ok")
                    .build();
            when(productApplicationService.addProductOptionValues(eq(productId), eq(pogId), any(AddProductOptionValuesCommand.class)))
                    .thenReturn(List.of(result));
            when(productResponseMapper.toAddProductOptionValueToGroupResponse(result)).thenReturn(response);

            mockMvc.perform(post("/admin/products/{productId}/option-groups/{productOptionGroupId}/option-values",
                            productId, pogId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.optionValues[0].productOptionValueId").value(povId.toString()));

            verify(productApplicationService).addProductOptionValues(eq(productId), eq(pogId), any(AddProductOptionValuesCommand.class));
        }
    }

    @Nested
    @DisplayName("POST /admin/products/{productId}/variants")
    class AddVariantTests {

        @Test
        @DisplayName("ADMIN이면 201과 본문 반환")
        @WithMockUser(authorities = "ADMIN")
        void withAdmin_Returns201() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID variantId = UUID.randomUUID();
            UUID selectedPov = UUID.randomUUID();

            AddProductVariantCommand command = AddProductVariantCommand.builder()
                    .stockQuantity(1)
                    .selectedProductOptionValueIds(Set.of(selectedPov))
                    .build();

            AddProductVariantsCommand bulkCommand = AddProductVariantsCommand.builder()
                    .variants(List.of(command))
                    .build();

            AddProductVariantResult result = AddProductVariantResult.builder()
                    .productId(productId)
                    .productVariantId(variantId)
                    .sku("PRD-TEST-SKU")
                    .stockQuantity(1)
                    .status(ProductStatus.ACTIVE)
                    .calculatedPrice(new BigDecimal("10000"))
                    .build();

            AddProductVariantResponse response = AddProductVariantResponse.builder()
                    .productId(productId)
                    .productVariantId(variantId)
                    .sku("PRD-TEST-SKU")
                    .stockQuantity(1)
                    .status("ACTIVE")
                    .calculatedPrice(new BigDecimal("10000"))
                    .message("ok")
                    .build();

            when(productApplicationService.addProductVariants(eq(productId), any(AddProductVariantsCommand.class)))
                    .thenReturn(List.of(result));
            when(productResponseMapper.toAddProductVariantResponse(result)).thenReturn(response);

            mockMvc.perform(post("/admin/products/{productId}/variants", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bulkCommand)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.variants[0].sku").value("PRD-TEST-SKU"));

            verify(productApplicationService).addProductVariants(eq(productId), any(AddProductVariantsCommand.class));
        }
    }

    @Nested
    @DisplayName("PATCH /admin/products/{productId}/variants/{productVariantId}")
    class UpdateVariantTests {

        @Test
        @DisplayName("ADMIN이면 200과 본문 반환")
        @WithMockUser(authorities = "ADMIN")
        void withAdmin_Returns200() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID variantId = UUID.randomUUID();

            UpdateProductVariantCommand command = UpdateProductVariantCommand.builder()
                    .stockQuantity(5)
                    .status(ProductStatus.ACTIVE)
                    .build();

            UpdateProductVariantResult result = UpdateProductVariantResult.builder()
                    .productId(productId)
                    .productVariantId(variantId)
                    .sku("PRD-TEST-SKU")
                    .stockQuantity(5)
                    .status(ProductStatus.ACTIVE)
                    .calculatedPrice(new BigDecimal("11000"))
                    .build();

            UpdateProductVariantResponse response = UpdateProductVariantResponse.builder()
                    .productId(productId)
                    .productVariantId(variantId)
                    .sku("PRD-TEST-SKU")
                    .stockQuantity(5)
                    .status("ACTIVE")
                    .calculatedPrice(new BigDecimal("11000"))
                    .message("ok")
                    .build();

            when(productApplicationService.updateProductVariant(eq(productId), eq(variantId), any(UpdateProductVariantCommand.class)))
                    .thenReturn(result);
            when(productResponseMapper.toUpdateProductVariantResponse(result)).thenReturn(response);

            mockMvc.perform(patch("/admin/products/{productId}/variants/{productVariantId}", productId, variantId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productVariantId").value(variantId.toString()))
                    .andExpect(jsonPath("$.stockQuantity").value(5))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            verify(productApplicationService)
                    .updateProductVariant(eq(productId), eq(variantId), any(UpdateProductVariantCommand.class));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/products/{productId}/option-groups/{productOptionGroupId}")
    class DeleteOptionGroupTests {
        @Test
        @DisplayName("ADMIN이면 200과 본문 반환")
        @WithMockUser(authorities = "ADMIN")
        void withAdmin_Returns200() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            DeleteProductOptionGroupResult result = DeleteProductOptionGroupResult.builder()
                    .productId(productId)
                    .productOptionGroupId(groupId)
                    .status(OptionStatus.DELETED)
                    .stepOrder(1024.0d)
                    .build();
            DeleteProductOptionGroupResponse response = DeleteProductOptionGroupResponse.builder()
                    .productId(productId)
                    .productOptionGroupId(groupId)
                    .status("DELETED")
                    .stepOrder(1024.0d)
                    .message("ok")
                    .build();

            when(productApplicationService.deleteProductOptionGroup(productId, groupId)).thenReturn(result);
            when(productResponseMapper.toDeleteProductOptionGroupResponse(result)).thenReturn(response);

            mockMvc.perform(delete("/admin/products/{productId}/option-groups/{productOptionGroupId}", productId, groupId)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELETED"));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/products/{productId}/option-values/{productOptionValueId}")
    class DeleteOptionValueTests {
        @Test
        @DisplayName("ADMIN이면 200과 본문 반환")
        @WithMockUser(authorities = "ADMIN")
        void withAdmin_Returns200() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();
            DeleteProductOptionValueResult result = DeleteProductOptionValueResult.builder()
                    .productId(productId)
                    .productOptionValueId(valueId)
                    .status(OptionStatus.DELETED)
                    .priceDelta(BigDecimal.ZERO)
                    .build();
            DeleteProductOptionValueResponse response = DeleteProductOptionValueResponse.builder()
                    .productId(productId)
                    .productOptionValueId(valueId)
                    .status("DELETED")
                    .priceDelta(BigDecimal.ZERO)
                    .message("ok")
                    .build();

            when(productApplicationService.deleteProductOptionValue(productId, valueId)).thenReturn(result);
            when(productResponseMapper.toDeleteProductOptionValueResponse(result)).thenReturn(response);

            mockMvc.perform(delete("/admin/products/{productId}/option-values/{productOptionValueId}", productId, valueId)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELETED"));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/products/{productId}/variants/{productVariantId}")
    class DeleteVariantTests {
        @Test
        @DisplayName("ADMIN이면 200과 본문 반환")
        @WithMockUser(authorities = "ADMIN")
        void withAdmin_Returns200() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID variantId = UUID.randomUUID();
            DeleteProductVariantResult result = DeleteProductVariantResult.builder()
                    .productId(productId)
                    .productVariantId(variantId)
                    .sku("PRD-TEST-SKU")
                    .status(ProductStatus.DELETED)
                    .build();
            DeleteProductVariantResponse response = DeleteProductVariantResponse.builder()
                    .productId(productId)
                    .productVariantId(variantId)
                    .sku("PRD-TEST-SKU")
                    .status("DELETED")
                    .message("ok")
                    .build();

            when(productApplicationService.deleteProductVariant(productId, variantId)).thenReturn(result);
            when(productResponseMapper.toDeleteProductVariantResponse(result)).thenReturn(response);

            mockMvc.perform(delete("/admin/products/{productId}/variants/{productVariantId}", productId, variantId)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productVariantId").value(variantId.toString()))
                    .andExpect(jsonPath("$.status").value("DELETED"));
        }
    }

    @Nested
    @DisplayName("PATCH /admin/products/{productId}/option-groups/reorder")
    class ReorderOptionGroupsTests {
        @Test
        @DisplayName("ADMIN이면 200과 본문 반환")
        @WithMockUser(authorities = "ADMIN")
        void withAdmin_Returns200() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID groupId1 = UUID.randomUUID();
            UUID groupId2 = UUID.randomUUID();

            ReorderProductOptionGroupsCommand command = ReorderProductOptionGroupsCommand.builder()
                    .orderedProductOptionGroupIds(List.of(groupId2, groupId1))
                    .build();

            ReorderProductOptionGroupsResult result = ReorderProductOptionGroupsResult.builder()
                    .productId(productId)
                    .updatedCount(2)
                    .build();
            ReorderProductOptionGroupsResponse response = ReorderProductOptionGroupsResponse.builder()
                    .productId(productId)
                    .updatedCount(2)
                    .message("ok")
                    .build();

            when(productApplicationService.reorderProductOptionGroups(eq(productId), any(ReorderProductOptionGroupsCommand.class)))
                    .thenReturn(result);
            when(productResponseMapper.toReorderProductOptionGroupsResponse(result)).thenReturn(response);

            mockMvc.perform(patch("/admin/products/{productId}/option-groups/reorder", productId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.updatedCount").value(2));

            verify(productApplicationService)
                    .reorderProductOptionGroups(eq(productId), any(ReorderProductOptionGroupsCommand.class));
        }
    }
}
