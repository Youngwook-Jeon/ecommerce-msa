package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.*;
import com.project.young.productservice.application.service.CategoryApplicationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("categories")
public class CategoryController {

    private final CategoryApplicationService categoryApplicationService;

    public CategoryController(CategoryApplicationService categoryApplicationService) {
        this.categoryApplicationService = categoryApplicationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<CreateCategoryResponse> create(@Valid @RequestBody CreateCategoryCommand command) {
        log.info("A post request to create Category: {}", command.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryApplicationService.createCategory(command));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UpdateCategoryResponse> update(
            @PathVariable("categoryId") Long categoryId,
            @Valid @RequestBody UpdateCategoryCommand command) {
        log.info("A put request to update Category with id: {}, command: {}", categoryId, command.getName());
        return ResponseEntity.ok(categoryApplicationService.updateCategory(categoryId, command));
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<DeleteCategoryResponse> delete(@PathVariable("categoryId") Long categoryId) {
        log.info("A delete request to delete Category with id: {}", categoryId);
        return ResponseEntity.ok(categoryApplicationService.deleteCategory(categoryId));
    }
}
