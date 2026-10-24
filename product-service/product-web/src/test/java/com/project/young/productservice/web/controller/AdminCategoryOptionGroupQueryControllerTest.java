package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.result.CategoryOptionGroupValuesResult;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionValueView;
import com.project.young.productservice.application.service.CategoryOptionGroupQueryService;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.exception.OptionGroupNotFoundException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
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
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithMockUser(authorities = "ADMIN")
    void returnsBulkCanonicalValuesForRepeatedOrCommaSeparatedParameters(boolean repeated) throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var memory = new ReadCategoryOptionGroupView(first, "RAM", "Memory", UUID.randomUUID(), "ram_capacity",
                "Memory capacity", AttributeDataType.NUMBER, "GB", true, 0);
        var release = new ReadCategoryOptionGroupView(second, "RELEASE", "Release", UUID.randomUUID(), "release_date",
                "Release date", AttributeDataType.DATE, null, false, 1);
        var value = new ReadCategoryOptionValueView(first, UUID.randomUUID(), "RAM16", "16 GB", 0,
                UUID.randomUUID(), memory.attributeId(), AttributeDataType.NUMBER, "16 gigabytes",
                null, new BigDecimal("16"), null, null);
        var date = new ReadCategoryOptionValueView(second, UUID.randomUUID(), "RELEASE2026", "2026 model", 0,
                UUID.randomUUID(), release.attributeId(), AttributeDataType.DATE, "Release day",
                null, null, null, LocalDate.of(2026, 3, 1));
        when(service.getAvailableOptionValues(4L, List.of(first, second))).thenReturn(List.of(
                new CategoryOptionGroupValuesResult(memory, List.of(value)),
                new CategoryOptionGroupValuesResult(release, List.of(date))));
        String[] parameters = repeated ? new String[]{first.toString(), second.toString()}
                : new String[]{first + "," + second};

        mvc.perform(get(PATH + "/values", 4).param("optionGroupIds", parameters))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].group.id").value(first.toString()))
                .andExpect(jsonPath("$[0].group.canonicalUnit").value("GB"))
                .andExpect(jsonPath("$[0].optionValues[0].id").value(value.id().toString()))
                .andExpect(jsonPath("$[0].optionValues[0].value").value("RAM16"))
                .andExpect(jsonPath("$[0].optionValues[0].displayName").value("16 GB"))
                .andExpect(jsonPath("$[0].optionValues[0].sortOrder").value(0))
                .andExpect(jsonPath("$[0].optionValues[0].attributeValue.id").value(value.attributeValueId().toString()))
                .andExpect(jsonPath("$[0].optionValues[0].attributeValue.attributeId").value(memory.attributeId().toString()))
                .andExpect(jsonPath("$[0].optionValues[0].attributeValue.displayName").value("16 gigabytes"))
                .andExpect(jsonPath("$[0].optionValues[0].attributeValue.dataType").value("NUMBER"))
                .andExpect(jsonPath("$[0].optionValues[0].attributeValue.numericValue").value(16))
                .andExpect(jsonPath("$[1].optionValues[0].attributeValue.dataType").value("DATE"))
                .andExpect(jsonPath("$[1].optionValues[0].attributeValue.dateValue").value("2026-03-01"));
        verify(service).getAvailableOptionValues(4L, List.of(first, second));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void preservesGroupsWithNoActiveValues() throws Exception {
        UUID id = UUID.randomUUID();
        var group = new ReadCategoryOptionGroupView(id, "RAM", "Memory", UUID.randomUUID(), "ram_capacity",
                "Memory capacity", AttributeDataType.NUMBER, "GB", true, 0);
        when(service.getAvailableOptionValues(4L, List.of(id)))
                .thenReturn(List.of(new CategoryOptionGroupValuesResult(group, List.of())));
        mvc.perform(get(PATH + "/values", 4).param("optionGroupIds", id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].group.id").value(id.toString()))
                .andExpect(jsonPath("$[0].optionValues.length()").value(0));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void rejectsUnavailableGroupWithoutReturningPartialValues() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getAvailableOptionValues(4L, List.of(id)))
                .thenThrow(new OptionGroupNotFoundException("One or more option groups are unavailable for this category"));
        mvc.perform(get(PATH + "/values", 4).param("optionGroupIds", id.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("Not Found"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void requiresGroupSelectionAndRejectsOversizedRequests() throws Exception {
        mvc.perform(get(PATH + "/values", 4)).andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/values", 4).param("optionGroupIds", "")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/values", 4).param("optionGroupIds", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/values", 4)
                        .param("optionGroupIds", String.join(",", Collections.nCopies(11, UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/values", 0).param("optionGroupIds", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = "CUSTOMER")
    void bulkValuesRequireAdminAuthority() throws Exception {
        mvc.perform(get(PATH + "/values", 4).param("optionGroupIds", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void bulkValuesRequireAuthentication() throws Exception {
        mvc.perform(get(PATH + "/values", 4).param("optionGroupIds", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

}
