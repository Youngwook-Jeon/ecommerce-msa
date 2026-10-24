package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.service.CategoryOptionGroupQueryService;
import com.project.young.productservice.web.dto.ReadCategoryOptionGroupQueryResponse;
import com.project.young.productservice.web.mapper.CategoryOptionGroupQueryResponseMapper;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
