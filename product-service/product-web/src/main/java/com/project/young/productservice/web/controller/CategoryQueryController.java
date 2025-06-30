package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.CategoryDto;
import com.project.young.productservice.application.service.CategoryQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("queries/categories")
@Slf4j
public class CategoryQueryController {

    private final CategoryQueryService categoryQueryService;

    public CategoryQueryController(CategoryQueryService categoryQueryService) {
        this.categoryQueryService = categoryQueryService;
    }

    @GetMapping("/hierarchy")
    public ResponseEntity<List<CategoryDto>> getAllActiveCategoryHierarchy() {
        log.info("REST request to get category hierarchy.");
        List<CategoryDto> categoryHierarchy = categoryQueryService.getAllActiveCategoryHierarchy();
        return ResponseEntity.ok(categoryHierarchy);
    }
}
