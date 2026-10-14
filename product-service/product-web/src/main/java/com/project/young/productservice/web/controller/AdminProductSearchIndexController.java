package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexBuildCommand;
import com.project.young.productservice.application.service.AdminProductSearchIndexService;
import com.project.young.productservice.application.service.AdminProductSearchIndexActivationService;
import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexActivationCommand;
import com.project.young.productservice.web.dto.AdminProductSearchIndexActivationRequest;
import com.project.young.productservice.web.dto.AdminProductSearchIndexActivationResponse;
import com.project.young.productservice.web.dto.AdminProductSearchIndexActivationJobResponse;
import com.project.young.productservice.web.dto.AdminProductSearchIndexBuildAcceptedResponse;
import com.project.young.productservice.web.dto.AdminProductSearchIndexBuildJobResponse;
import com.project.young.productservice.web.dto.AdminProductSearchIndexBuildRequest;
import com.project.young.productservice.web.dto.AdminProductSearchIndexVersionPageResponse;
import com.project.young.productservice.web.mapper.AdminProductSearchIndexResponseMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/admin/product-search")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminProductSearchIndexController {
    private final AdminProductSearchIndexService service;
    private final AdminProductSearchIndexResponseMapper mapper;
    private final AdminProductSearchIndexActivationService activations;

    public AdminProductSearchIndexController(AdminProductSearchIndexService service, AdminProductSearchIndexResponseMapper mapper,
                                             AdminProductSearchIndexActivationService activations) {
        this.service = service;
        this.mapper = mapper;
        this.activations = activations;
    }

    @PostMapping("/index-builds")
    public ResponseEntity<AdminProductSearchIndexBuildAcceptedResponse> register(
            @AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AdminProductSearchIndexBuildRequest request) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("Authenticated administrator subject is required");
        }
        var job = service.register(new RegisterProductSearchIndexBuildCommand(jwt.getSubject(), key,
                request.batchSize(), Boolean.TRUE.equals(request.catalogWritesPaused())));
        return ResponseEntity.accepted().location(URI.create("/admin/product-search/index-builds/" + job.id()))
                .body(new AdminProductSearchIndexBuildAcceptedResponse(job.id(), job.indexVersionId(), job.status()));
    }

    @PostMapping("/index-versions/{versionId}/activate")
    public ResponseEntity<AdminProductSearchIndexActivationResponse> activate(@PathVariable UUID versionId,
            @AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AdminProductSearchIndexActivationRequest request) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("Authenticated administrator subject is required");
        }
        var job = activations.register(new RegisterProductSearchIndexActivationCommand(versionId, request.expectedActiveVersionId(),
                jwt.getSubject(), key, Boolean.TRUE.equals(request.catalogWritesPaused())));
        return ResponseEntity.accepted().location(URI.create("/admin/product-search/index-activations/" + job.id()))
                .body(new AdminProductSearchIndexActivationResponse(job.id(), job.indexVersionId(), job.expectedActiveVersionId(), job.status()));
    }

    @GetMapping("/index-activations/{jobId}")
    public AdminProductSearchIndexActivationJobResponse getActivation(@PathVariable UUID jobId) {
        return mapper.toActivationResponse(activations.getJob(jobId));
    }

    @GetMapping("/index-builds/{jobId}")
    public AdminProductSearchIndexBuildJobResponse getJob(@PathVariable UUID jobId) {
        return mapper.toJobResponse(service.getJob(jobId));
    }

    @GetMapping("/index-versions")
    public AdminProductSearchIndexVersionPageResponse listVersions(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = service.listVersions(page, size);
        return new AdminProductSearchIndexVersionPageResponse(result.content().stream().map(mapper::toVersionResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
