package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.command.SetProductSpecificationCommand;
import com.project.young.productservice.application.service.ProductSpecificationService;
import com.project.young.productservice.web.dto.AttributeCatalogResponses;
import com.project.young.productservice.web.mapper.AttributeCatalogResponseMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("admin/products/{productId}/specifications")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class AdminProductSpecificationController {
    private final ProductSpecificationService service;
    private final AttributeCatalogResponseMapper mapper;

    public AdminProductSpecificationController(ProductSpecificationService service, AttributeCatalogResponseMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Includes inactive definitions for historical reads; version and specifications share one snapshot. */
    @GetMapping
    public ResponseEntity<SpecificationsResponse> specifications(@PathVariable UUID productId) {
        log.debug("REST request to get product specifications. productId={}", productId);
        var result = service.specifications(productId);
        return ResponseEntity.ok(new SpecificationsResponse(productId, result.version(), result.specifications().stream()
                .map(entry -> new SpecificationResponse(mapper.definition(entry.attribute()), mapper.value(entry.value()))).toList()));
    }

    /**
     * Sets one canonical fixed value using the product catalog version. The definition must be active
     * and explicitly enabled on the product category. A non-deleted purchase option for the same
     * definition is rejected. ACTIVE products must still satisfy every required attribute.
     * Stale writes return 409; reload specifications after success to obtain the next version.
     */
    @PutMapping("/{attributeId}")
    public ResponseEntity<Void> setSpecification(@PathVariable UUID productId, @PathVariable UUID attributeId,
                                                @Valid @RequestBody SetProductSpecificationCommand command,
                                                @RequestParam("expectedVersion") @Positive long expectedVersion) {
        log.info("REST request to set product specification. productId={}, attributeId={}", productId, attributeId);
        service.setSpecification(productId, attributeId, command, expectedVersion);
        return ResponseEntity.noContent().build();
    }

    /** DRAFT may be incomplete; removing the only representation of a required ACTIVE attribute is rejected. */
    @DeleteMapping("/{attributeId}")
    public ResponseEntity<Void> removeSpecification(@PathVariable UUID productId, @PathVariable UUID attributeId,
                                                   @RequestParam("expectedVersion") @Positive long expectedVersion) {
        log.info("REST request to remove product specification. productId={}, attributeId={}", productId, attributeId);
        service.removeSpecification(productId, attributeId, expectedVersion);
        return ResponseEntity.noContent().build();
    }

    public record SpecificationResponse(AttributeCatalogResponses.Definition attribute, AttributeCatalogResponses.Value value) {
    }

    public record SpecificationsResponse(UUID productId, long version, List<SpecificationResponse> specifications) {
    }
}
