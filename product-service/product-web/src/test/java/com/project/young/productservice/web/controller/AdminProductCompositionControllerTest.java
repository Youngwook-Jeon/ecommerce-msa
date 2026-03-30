package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.command.AddProductOptionGroupCommand;
import com.project.young.productservice.application.dto.command.AddProductOptionValueCommand;
import com.project.young.productservice.application.dto.command.AddProductVariantCommand;
import com.project.young.productservice.application.dto.command.AddProductVariantsCommand;
import com.project.young.productservice.application.dto.result.AddProductOptionGroupResult;
import com.project.young.productservice.application.dto.result.AddProductOptionValueToGroupResult;
import com.project.young.productservice.application.dto.result.AddProductVariantResult;
import com.project.young.productservice.application.service.ProductApplicationService;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.dto.AddProductOptionGroupResponse;
import com.project.young.productservice.web.dto.AddProductOptionValueToGroupResponse;
import com.project.young.productservice.web.dto.AddProductVariantResponse;
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
                    .stepOrder(1)
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
                    .stepOrder(1)
                    .required(true)
                    .optionValueCount(1)
                    .build();

            AddProductOptionGroupResponse response = AddProductOptionGroupResponse.builder()
                    .productId(productId)
                    .productOptionGroupId(pogId)
                    .optionGroupId(globalOgId)
                    .stepOrder(1)
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

            AddProductOptionValueCommand command = AddProductOptionValueCommand.builder()
                    .optionValueId(globalValId)
                    .priceDelta(BigDecimal.ZERO)
                    .isDefault(true)
                    .isActive(true)
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

            when(productApplicationService.addProductOptionValue(eq(productId), eq(pogId), any(AddProductOptionValueCommand.class)))
                    .thenReturn(result);
            when(productResponseMapper.toAddProductOptionValueToGroupResponse(result)).thenReturn(response);

            mockMvc.perform(post("/admin/products/{productId}/option-groups/{productOptionGroupId}/option-values",
                            productId, pogId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.productOptionValueId").value(povId.toString()));

            verify(productApplicationService).addProductOptionValue(eq(productId), eq(pogId), any(AddProductOptionValueCommand.class));
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
}
