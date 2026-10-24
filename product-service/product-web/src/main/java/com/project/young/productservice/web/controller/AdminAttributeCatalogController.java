package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.command.CreateAttributeDefinitionCommand;
import com.project.young.productservice.application.dto.command.CreateAttributeValueCommand;
import com.project.young.productservice.application.dto.command.SetCategoryAttributeCommand;
import com.project.young.productservice.application.service.AttributeCatalogService;
import com.project.young.productservice.web.dto.AttributeCatalogRequests;
import com.project.young.productservice.web.dto.AttributeCatalogResponses;
import com.project.young.productservice.web.mapper.AttributeCatalogResponseMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminAttributeCatalogController {
    private final AttributeCatalogService service;
    private final AttributeCatalogResponseMapper mapper;

    public AdminAttributeCatalogController(AttributeCatalogService service, AttributeCatalogResponseMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lists active and inactive definitions so administrators can inspect existing references. */
    @GetMapping("/attributes")
    public ResponseEntity<List<AttributeCatalogResponses.Definition>> definitions() {
        return ResponseEntity.ok(service.definitions().stream().map(mapper::definition).toList());
    }

    @GetMapping("/attributes/{attributeId}")
    public ResponseEntity<AttributeCatalogResponses.Definition> definition(@PathVariable UUID attributeId) {
        return ResponseEntity.ok(mapper.definition(service.definition(attributeId)));
    }

    @PostMapping("/attributes")
    public ResponseEntity<AttributeCatalogResponses.Definition> create(
            @Valid @RequestBody AttributeCatalogRequests.Definition request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.definition(service.createDefinition(new CreateAttributeDefinitionCommand(request.code(),
                        request.displayName(), request.dataType(), request.canonicalUnit()))));
    }

    /** Renames presentation only; an inactive definition remains inactive. */
    @PutMapping("/attributes/{attributeId}")
    public ResponseEntity<AttributeCatalogResponses.Definition> rename(
            @PathVariable UUID attributeId, @Valid @RequestBody AttributeCatalogRequests.Rename request) {
        return ResponseEntity.ok(mapper.definition(service.renameDefinition(attributeId, request.displayName())));
    }

    /**
     * Idempotently deactivates a definition without deleting values or category policies.
     * New values and enabled category assignments return 409 ATTRIBUTE_INACTIVE afterwards.
     * Existing metadata remains readable; effective usability and filter/option availability become false.
     * Product specifications and purchase-option writes are not connected in this metadata stage;
     * their future write paths must enforce the same active-definition rule and preserve existing references.
     */
    @PostMapping("/attributes/{attributeId}/deactivate")
    public ResponseEntity<AttributeCatalogResponses.Definition> deactivate(@PathVariable UUID attributeId) {
        return ResponseEntity.ok(mapper.definition(service.deactivateDefinition(attributeId)));
    }

    /**
     * Physically deletes an unreferenced definition only; values or category policies cause
     * 409 ATTRIBUTE_IN_USE. Deactivate definitions in normal operation to preserve their history.
     * Future product-specification and option-mapping references must also use restrictive FKs.
     */
    @DeleteMapping("/attributes/{attributeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID attributeId) {
        service.deleteDefinition(attributeId);
        return ResponseEntity.noContent().build();
    }

    /** Includes existing values of inactive definitions for historical reads and administration. */
    @GetMapping("/attributes/{attributeId}/values")
    public ResponseEntity<List<AttributeCatalogResponses.Value>> values(@PathVariable UUID attributeId) {
        return ResponseEntity.ok(service.values(attributeId).stream().map(mapper::value).toList());
    }

    /** Creates a canonical value only for an active definition; otherwise returns 409 ATTRIBUTE_INACTIVE. */
    @PostMapping("/attributes/{attributeId}/values")
    public ResponseEntity<AttributeCatalogResponses.Value> createValue(
            @PathVariable UUID attributeId, @Valid @RequestBody AttributeCatalogRequests.Value request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.value(service.createValue(attributeId, new CreateAttributeValueCommand(request.displayName(),
                        request.textValue(), request.numericValue(), request.booleanValue(), request.dateValue()))));
    }

    @PutMapping("/attributes/{attributeId}/values/{valueId}")
    public ResponseEntity<AttributeCatalogResponses.Value> renameValue(
            @PathVariable UUID attributeId, @PathVariable UUID valueId,
            @Valid @RequestBody AttributeCatalogRequests.Rename request) {
        return ResponseEntity.ok(mapper.value(service.renameValue(attributeId, valueId, request.displayName())));
    }

    /** Referenced option dictionary values cannot be deleted and return 409 ATTRIBUTE_IN_USE. */
    @DeleteMapping("/attributes/{attributeId}/values/{valueId}")
    public ResponseEntity<Void> deleteValue(@PathVariable UUID attributeId, @PathVariable UUID valueId) {
        service.deleteValue(attributeId, valueId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists only policies explicitly registered on this category; parent policies are never inherited.
     * Parent status and tree placement do not affect attribute policy eligibility.
     * Inactive definitions and disabled policies remain visible. Use usable, optionAvailable and
     * filterAvailable for eligibility; the other flags retain their explicitly configured values.
     */
    @GetMapping("/categories/{categoryId}/attributes")
    public ResponseEntity<List<AttributeCatalogResponses.CategoryAttribute>> categoryPolicies(
            @PathVariable @Positive Long categoryId) {
        return ResponseEntity.ok(service.categoryPolicies(categoryId).stream().map(mapper::policy).toList());
    }

    /**
     * Creates or replaces an explicit policy for this category only. Enabling an inactive definition returns 409 ATTRIBUTE_INACTIVE;
     * explicit exclusions remain permitted so administrators can retire existing assignments.
     */
    @PutMapping("/categories/{categoryId}/attributes/{attributeId}")
    public ResponseEntity<Void> setPolicy(
            @PathVariable @Positive Long categoryId, @PathVariable UUID attributeId,
            @Valid @RequestBody AttributeCatalogRequests.Policy request) {
        service.setPolicy(categoryId, attributeId, new SetCategoryAttributeCommand(request.enabled(),
                request.required(), request.optionAllowed(), request.filterable(), request.displayOrder()));
        return ResponseEntity.noContent().build();
    }

    /** Removes this category's policy without restoring or copying any parent policy. */
    @DeleteMapping("/categories/{categoryId}/attributes/{attributeId}")
    public ResponseEntity<Void> removePolicy(@PathVariable @Positive Long categoryId, @PathVariable UUID attributeId) {
        service.removePolicy(categoryId, attributeId);
        return ResponseEntity.noContent().build();
    }
}
