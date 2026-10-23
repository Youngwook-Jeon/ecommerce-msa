package com.project.young.productservice.web.controller;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.productservice.application.dto.result.CategoryAttributeResult;
import com.project.young.productservice.application.service.AttributeCatalogService;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.exception.AttributeInactiveException;
import com.project.young.productservice.domain.exception.AttributeInUseException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.CategoryAttributePolicy;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.exception.handler.AttributeCatalogExceptionHandler;
import com.project.young.productservice.web.mapper.AttributeCatalogResponseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAttributeCatalogController.class)
@Import({SecurityConfig.class, TestConfig.class, AttributeCatalogResponseMapper.class, AttributeCatalogExceptionHandler.class})
class AdminAttributeCatalogControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    AttributeCatalogService service;
    private final UUID id = UUID.randomUUID();

    @Test
    @WithMockUser(authorities = "ADMIN")
    void createsDefinitionAndMapsItsPublicResponse() throws Exception {
        when(service.createDefinition(any())).thenReturn(
                new AttributeDefinition(new AttributeDefinitionId(id), "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB"));
        mvc.perform(post("/admin/attributes").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"ram_capacity","displayName":"Memory","dataType":"NUMBER","canonicalUnit":"GB"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.dataType").value("NUMBER"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void deactivatesDefinitionAndReturnsItsRetainedIdentity() throws Exception {
        when(service.deactivateDefinition(id)).thenReturn(
                new AttributeDefinition(new AttributeDefinitionId(id), "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB").deactivate());
        mvc.perform(post("/admin/attributes/{id}/deactivate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        verify(service).deactivateDefinition(id);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void inactiveAttributesRemainVisibleButCannotSupplyFiltersOrOptions() throws Exception {
        var definition = new AttributeDefinition(new AttributeDefinitionId(id), "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB").deactivate();
        when(service.categoryPolicies(4L)).thenReturn(List.of(new CategoryAttributeResult(definition,
                new CategoryAttributePolicy(4L, id, true, true, true, true, 10))));
        mvc.perform(get("/admin/categories/4/attributes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attribute.status").value("INACTIVE"))
                .andExpect(jsonPath("$[0].policy.enabled").value(true))
                .andExpect(jsonPath("$[0].policy.usable").value(false))
                .andExpect(jsonPath("$[0].policy.optionAvailable").value(false))
                .andExpect(jsonPath("$[0].policy.filterAvailable").value(false));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void returnsSpecificLifecycleConflicts() throws Exception {
        doThrow(new AttributeInUseException()).when(service).deleteDefinition(id);
        mvc.perform(delete("/admin/attributes/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_IN_USE"));
        when(service.createValue(eq(id), any())).thenThrow(new AttributeInactiveException());
        mvc.perform(post("/admin/attributes/{id}/values", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"32 GB\",\"numericValue\":32}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_INACTIVE"));
        doThrow(new AttributeInactiveException()).when(service).setPolicy(eq(4L), eq(id), any());
        mvc.perform(put("/admin/categories/4/attributes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"enabled":true,"required":false,"optionAllowed":true,"filterable":true,"displayOrder":0}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_INACTIVE"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void exposesExplicitPoliciesWithoutInheritanceMetadata() throws Exception {
        var definition = new AttributeDefinition(new AttributeDefinitionId(id), "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB");
        when(service.categoryPolicies(4L)).thenReturn(List.of(new CategoryAttributeResult(definition,
                new CategoryAttributePolicy(4L, id, false, false, false, false, 10))));
        mvc.perform(get("/admin/categories/4/attributes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attribute.code").value("ram_capacity"))
                .andExpect(jsonPath("$[0].policy.enabled").value(false))
                .andExpect(jsonPath("$[0].policy.sourceCategoryId").doesNotExist())
                .andExpect(jsonPath("$[0].policy.sourceCategoryName").doesNotExist())
                .andExpect(jsonPath("$[0].policy.inherited").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void validatesAllPolicyFieldsRatherThanDefaultingMissingBooleans() throws Exception {
        mvc.perform(put("/admin/categories/4/attributes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ATTRIBUTE_CATALOG_REQUEST"));
        mvc.perform(get("/admin/categories/0/attributes")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/attributes/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(post("/admin/attributes").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"RAM","displayName":"Memory","dataType":"NUMBER"}
                """)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void savesAndRemovesExplicitCategoryPolicies() throws Exception {
        mvc.perform(put("/admin/categories/4/attributes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"enabled":true,"required":true,"optionAllowed":true,"filterable":true,"displayOrder":10}
                        """))
                .andExpect(status().isNoContent());
        verify(service).setPolicy(eq(4L), eq(id), any());
        mvc.perform(delete("/admin/categories/4/attributes/{id}", id)).andExpect(status().isNoContent());
        verify(service).removePolicy(4L, id);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void returnsStableNotFoundAndSanitizedConflictErrors() throws Exception {
        when(service.definition(id)).thenThrow(new AttributeNotFoundException("Attribute definition not found"));
        mvc.perform(get("/admin/attributes/{id}", id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_CATALOG_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Attribute definition not found"));
        doThrow(new DataIntegrityViolationException("Internal SQL must not be returned")).when(service).deleteDefinition(id);
        mvc.perform(delete("/admin/attributes/{id}", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_CATALOG_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "The attribute code or value already exists, a reference prevents deletion, or a concurrent change conflicts"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void returnsDomainValidationErrorsAndRejectsMalformedJson() throws Exception {
        when(service.createDefinition(any())).thenThrow(new AttributeDomainException(
                "Only numeric attributes may have a nonblank unit of at most 20 characters"));
        mvc.perform(post("/admin/attributes").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"color","displayName":"Color","dataType":"ENUM","canonicalUnit":"GB"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ATTRIBUTE_CATALOG_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "Only numeric attributes may have a nonblank unit of at most 20 characters"));
        mvc.perform(post("/admin/attributes").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ATTRIBUTE_CATALOG_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid attribute catalog request"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void consistencyFailuresReturnSanitizedInternalErrors() throws Exception {
        when(service.categoryPolicies(4L)).thenThrow(new IllegalStateException(
                "Attribute definition missing for category policy: attributeId=" + id));
        mvc.perform(get("/admin/categories/4/attributes"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }

    @Test
    @WithMockUser(authorities = "CUSTOMER")
    void deniesReadsAndWritesWithoutAdminAuthority() throws Exception {
        mvc.perform(get("/admin/attributes")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/attributes").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"ram","displayName":"RAM","dataType":"NUMBER"}
                """)).andExpect(status().isForbidden());
        mvc.perform(delete("/admin/categories/4/attributes/{id}", id)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/attributes/{id}/deactivate", id)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void deniesAnonymousRequests() throws Exception {
        mvc.perform(get("/admin/attributes")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
