package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexBuildCommand;
import com.project.young.productservice.application.dto.result.ProductSearchIndexVersionPageResult;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.application.service.AdminProductSearchIndexService;
import com.project.young.productservice.application.service.AdminProductSearchIndexActivationService;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.mapper.AdminProductSearchIndexResponseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexActivationCommand;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProductSearchIndexController.class)
@Import({SecurityConfig.class, TestConfig.class, AdminProductSearchIndexResponseMapper.class})
class AdminProductSearchIndexControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private AdminProductSearchIndexService service;
    @MockitoBean
    private AdminProductSearchIndexActivationService activations;

    private static final String PREFIX = "/admin/product-search";

    @Test
    void activate_asAdmin_registersIntentAndReturnsJobLocation() throws Exception {
        var job = ProductSearchIndexActivationJob.queue(UUID.randomUUID(), UUID.randomUUID(), null,
                "products-read", "admin-sub", "activate-1", Instant.now());
        when(activations.register(any())).thenReturn(job);
        mvc.perform(post(PREFIX + "/index-versions/" + job.indexVersionId() + "/activate")
                        .with(jwt().jwt(j -> j.subject("admin-sub").claim("preferred_username", "display-name"))
                                .authorities(new SimpleGrantedAuthority("ADMIN")))
                        .header("Idempotency-Key", "activate-1").contentType("application/json")
                        .content("{\"expectedActiveVersionId\":null,\"catalogWritesPaused\":true}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", PREFIX + "/index-activations/" + job.id()))
                .andExpect(jsonPath("$.jobId").value(job.id().toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
        verify(activations).register(new RegisterProductSearchIndexActivationCommand(job.indexVersionId(), null, "admin-sub", "activate-1", true));
    }

    @Test
    void getActivation_asAdmin_returnsUnconfirmedStatusAndSafeFailure() throws Exception {
        Instant now = Instant.now();
        var job = ProductSearchIndexActivationJob.queue(UUID.randomUUID(), UUID.randomUUID(), null,
                "products-read", "admin-sub", "activate-1", now).start(now).beginSwitch(now)
                .markUnconfirmed("UNKNOWN", "결과 확인 필요", now);
        when(activations.getJob(job.id())).thenReturn(job);
        mvc.perform(get(PREFIX + "/index-activations/" + job.id()).with(jwt().authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNCONFIRMED"))
                .andExpect(jsonPath("$.failure.code").value("UNKNOWN"))
                .andExpect(jsonPath("$.ownerToken").doesNotExist());
    }

    @Test
    void activate_invalidBodyOrMissingHeader_rejectsBeforeService() throws Exception {
        String path = PREFIX + "/index-versions/" + UUID.randomUUID() + "/activate";
        mvc.perform(post(path).with(jwt().authorities(new SimpleGrantedAuthority("ADMIN")))
                        .header("Idempotency-Key", "activate-1").contentType("application/json")
                        .content("{\"catalogWritesPaused\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).with(jwt().authorities(new SimpleGrantedAuthority("ADMIN")))
                        .contentType("application/json").content("{\"catalogWritesPaused\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(activations);
    }

    @Test
    void activate_withoutAdminAuthority_rejectsAccess() throws Exception {
        String path = PREFIX + "/index-versions/" + UUID.randomUUID() + "/activate";
        mvc.perform(post(path).contentType("application/json").content("{\"catalogWritesPaused\":true}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(jwt().authorities(new SimpleGrantedAuthority("CUSTOMER")))
                        .header("Idempotency-Key", "activate-1").contentType("application/json")
                        .content("{\"catalogWritesPaused\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(activations);
    }

    @Test
    void activate_conflict_returnsSpecificErrorCode() throws Exception {
        when(activations.register(any())).thenThrow(new ProductSearchIndexManagementException(
                ProductSearchIndexManagementException.Code.ACTIVE_INDEX_CHANGED, "Active index version changed"));
        mvc.perform(post(PREFIX + "/index-versions/" + UUID.randomUUID() + "/activate")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ADMIN")))
                        .header("Idempotency-Key", "activate-1").contentType("application/json")
                        .content("{\"catalogWritesPaused\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTIVE_INDEX_CHANGED"));
    }

    @Test
    void register_asAdmin_usesJwtSubjectAndReturnsAcceptedLocation() throws Exception {
        var job = job();
        when(service.register(any())).thenReturn(job);
        mvc.perform(post(PREFIX + "/index-builds")
                        .with(jwt().jwt(j -> j.subject("admin-sub").claim("preferred_username", "display-name"))
                                .authorities(new SimpleGrantedAuthority("ADMIN")))
                        .header("Idempotency-Key", "request-1").contentType("application/json")
                        .content("{\"catalogWritesPaused\":true}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", PREFIX + "/index-builds/" + job.id()))
                .andExpect(jsonPath("$.jobId").value(job.id().toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
        verify(service).register(new RegisterProductSearchIndexBuildCommand("admin-sub", "request-1", 500, true));
    }

    @Test
    void getJob_asAdmin_returnsMappedProgress() throws Exception {
        var job = job();
        when(service.getJob(job.id())).thenReturn(job);
        mvc.perform(get(PREFIX + "/index-builds/" + job.id()).with(jwt().authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.progress.indexedProducts").value(0))
                .andExpect(jsonPath("$.requestedBy").value("admin-sub"));
    }

    @Test
    void listVersions_withDefaults_returnsEmptyPage() throws Exception {
        when(service.listVersions(0, 20)).thenReturn(new ProductSearchIndexVersionPageResult(List.of(), 0, 20, 0));
        mvc.perform(get(PREFIX + "/index-versions").with(jwt().authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void routes_withoutAdminAuthority_rejectAccess() throws Exception {
        mvc.perform(get(PREFIX + "/index-versions")).andExpect(status().isUnauthorized());
        mvc.perform(get(PREFIX + "/index-versions").with(jwt().authorities(new SimpleGrantedAuthority("CUSTOMER"))))
                .andExpect(status().isForbidden());
        mvc.perform(post(PREFIX + "/index-builds").with(jwt().authorities(new SimpleGrantedAuthority("CUSTOMER")))
                        .header("Idempotency-Key", "request-1").contentType("application/json")
                        .content("{\"catalogWritesPaused\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void invalidBodyHeaderOrUuid_returnsContractError() throws Exception {
        mvc.perform(post(PREFIX + "/index-builds").with(jwt().authorities(new SimpleGrantedAuthority("ADMIN")))
                        .contentType("application/json").content("{\"catalogWritesPaused\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INDEX_MANAGEMENT_REQUEST"));
        mvc.perform(post(PREFIX + "/index-builds").with(jwt().authorities(new SimpleGrantedAuthority("ADMIN")))
                        .header("Idempotency-Key", "request-1").contentType("application/json")
                        .content("{\"batchSize\":0,\"catalogWritesPaused\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INDEX_MANAGEMENT_REQUEST"));
        mvc.perform(get(PREFIX + "/index-builds/not-a-uuid").with(jwt().authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INDEX_MANAGEMENT_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void managementFailures_mapToNotFoundConflictAndUnavailable() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getJob(id)).thenThrow(new ProductSearchIndexManagementException(
                ProductSearchIndexManagementException.Code.INDEX_BUILD_NOT_FOUND, "Missing job"));
        mvc.perform(get(PREFIX + "/index-builds/" + id).with(jwt().authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INDEX_BUILD_NOT_FOUND"));
        when(service.register(any())).thenThrow(new ProductSearchIndexManagementException(
                ProductSearchIndexManagementException.Code.INDEX_BUILD_IN_PROGRESS, "Busy"));
        mvc.perform(post(PREFIX + "/index-builds").with(jwt().authorities(new SimpleGrantedAuthority("ADMIN")))
                        .header("Idempotency-Key", "request-1").contentType("application/json").content("{\"catalogWritesPaused\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INDEX_BUILD_IN_PROGRESS"));
        when(service.listVersions(0, 20)).thenThrow(new ProductSearchIndexManagementException(
                ProductSearchIndexManagementException.Code.INDEX_MANAGEMENT_UNAVAILABLE, "Unavailable"));
        mvc.perform(get(PREFIX + "/index-versions").with(jwt().authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void transactionFailure_returnsUnavailableWithoutProviderDetails() throws Exception {
        when(service.listVersions(0, 20)).thenThrow(new CannotCreateTransactionException("private DB details"));
        mvc.perform(get(PREFIX + "/index-versions").with(jwt().authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INDEX_MANAGEMENT_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Index management is unavailable"));
    }

    private static ProductSearchIndexBuildJob job() {
        return ProductSearchIndexBuildJob.queue(UUID.randomUUID(), UUID.randomUUID(), "admin-sub", "request-1", 500, Instant.now());
    }
}
