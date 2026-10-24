package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.service.CategoryOptionGroupQueryService;
import com.project.young.productservice.web.dto.ReadCategoryOptionGroupQueryResponse;
import com.project.young.productservice.web.dto.ReadCategoryOptionGroupValuesQueryResponse;
import com.project.young.productservice.web.mapper.CategoryOptionGroupQueryResponseMapper;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/queries/categories/{categoryId}/option-groups")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminCategoryOptionGroupQueryController {
    private final CategoryOptionGroupQueryService service;
    private final CategoryOptionGroupQueryResponseMapper mapper;

    public AdminCategoryOptionGroupQueryController(CategoryOptionGroupQueryService service,
                                                   CategoryOptionGroupQueryResponseMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /**
     * Lists active dictionary groups linked to active definitions explicitly enabled for options on this category.
     * Parent policies are never inherited. Inactive categories remain inspectable; missing or soft-deleted
     * categories return 404. A category with no eligible groups returns an empty list.
     * Sorted by policy displayOrder, attribute code and group ID. No option values are loaded.
     * attributeRequired describes the category specification policy, not mandatory purchase selection.
     * This is a selection aid; product option writes must independently revalidate current policies.
     */
    @GetMapping
    public ResponseEntity<List<ReadCategoryOptionGroupQueryResponse>> getAvailableOptionGroups(
            @PathVariable @Positive Long categoryId) {
        return ResponseEntity.ok(service.getAvailableOptionGroups(categoryId).stream().map(mapper::toResponse).toList());
    }

    /**
     * Returns active linked option values and their canonical typed attribute values for 1 to 10 selected groups.
     * Accepts comma-separated or repeated optionGroupIds query parameters; duplicate IDs are collapsed.
     * Every requested group must be available on this category; otherwise returns 404 without partial results.
     * Groups follow policy order and include empty optionValues lists. Values follow sortOrder, then value ID.
     * Group metadata supplies canonicalUnit; attributeValue contains the canonical ID, type and typed value.
     * Reads share one snapshot; product option writes must still revalidate current policies.
     */
    @GetMapping("/values")
    public ResponseEntity<List<ReadCategoryOptionGroupValuesQueryResponse>> getAvailableOptionValues(
            @PathVariable @Positive Long categoryId,
            @RequestParam @NotEmpty @Size(max = CategoryOptionGroupQueryService.MAX_OPTION_GROUPS_PER_REQUEST)
            List<UUID> optionGroupIds) {
        return ResponseEntity.ok(service.getAvailableOptionValues(categoryId, optionGroupIds).stream()
                .map(mapper::toValuesResponse).toList());
    }
}
