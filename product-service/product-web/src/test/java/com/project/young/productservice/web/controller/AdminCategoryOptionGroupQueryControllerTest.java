package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.application.service.CategoryOptionGroupQueryService;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.mapper.CategoryOptionGroupQueryResponseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCategoryOptionGroupQueryController.class)
@Import({SecurityConfig.class, TestConfig.class, CategoryOptionGroupQueryResponseMapper.class})
class AdminCategoryOptionGroupQueryControllerTest {
    private static final String PATH = "/admin/queries/categories/{categoryId}/option-groups";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    CategoryOptionGroupQueryService service;

    @Test
    @WithMockUser(authorities = "ADMIN")
    void returnsGroupAndAttributeMetadataWithoutOptionValues() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        when(service.getAvailableOptionGroups(4L)).thenReturn(List.of(new ReadCategoryOptionGroupView(
                groupId, "RAM", "Memory", attributeId, "ram_capacity", "RAM capacity",
                AttributeDataType.NUMBER, "GB", true, 10)));

        mvc.perform(get(PATH, 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(groupId.toString()))
                .andExpect(jsonPath("$[0].name").value("RAM"))
                .andExpect(jsonPath("$[0].displayName").value("Memory"))
                .andExpect(jsonPath("$[0].attributeId").value(attributeId.toString()))
                .andExpect(jsonPath("$[0].attributeCode").value("ram_capacity"))
                .andExpect(jsonPath("$[0].attributeDisplayName").value("RAM capacity"))
                .andExpect(jsonPath("$[0].attributeDataType").value("NUMBER"))
                .andExpect(jsonPath("$[0].canonicalUnit").value("GB"))
                .andExpect(jsonPath("$[0].attributeRequired").value(true))
                .andExpect(jsonPath("$[0].displayOrder").value(10))
                .andExpect(jsonPath("$[0].optionValues").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void returnsEmptyArrayForCategoryWithoutEligibleGroups() throws Exception {
        when(service.getAvailableOptionGroups(4L)).thenReturn(List.of());
        mvc.perform(get(PATH, 4)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void returnsNotFoundUsingTheExistingCategoryErrorContract() throws Exception {
        when(service.getAvailableOptionGroups(4L)).thenThrow(new CategoryNotFoundException("Category not found: 4"));
        mvc.perform(get(PATH, 4)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("Not Found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid"})
    @WithMockUser(authorities = "ADMIN")
    void rejectsInvalidCategoryId(String categoryId) throws Exception {
        mvc.perform(get(PATH, categoryId)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = "CUSTOMER")
    void requiresAdminAuthority() throws Exception {
        mvc.perform(get(PATH, 4)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(PATH, 4)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
