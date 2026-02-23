package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.port.output.view.ReadCategoryView;
import com.project.young.productservice.application.service.CategoryQueryService;
import com.project.young.productservice.web.dto.ReadCategoryResponse;
import com.project.young.productservice.web.mapper.CategoryQueryResponseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("queries/categories")
@Slf4j
public class CategoryQueryController {

    private final CategoryQueryService categoryQueryService;
    private final CategoryQueryResponseMapper categoryQueryResponseMapper;

    public CategoryQueryController(CategoryQueryService categoryQueryService,
                                   CategoryQueryResponseMapper categoryQueryResponseMapper) {
        this.categoryQueryService = categoryQueryService;
        this.categoryQueryResponseMapper = categoryQueryResponseMapper;
    }

    @GetMapping("/hierarchy")
    public ResponseEntity<ReadCategoryResponse> getAllActiveCategoryHierarchy() {
        log.info("REST request to get category hierarchy.");
        List<ReadCategoryView> categoryHierarchy = categoryQueryService.getAllActiveCategoryHierarchy();
        return ResponseEntity.ok(categoryQueryResponseMapper.toReadCategoryResponse(categoryHierarchy));
    }

    @GetMapping("/admin/hierarchy")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ReadCategoryResponse> getAdminCategoryHierarchy() {
        log.info("REST request to get category hierarchy for admin.");
        List<ReadCategoryView> categoryHierarchy = categoryQueryService.getAdminCategoryHierarchy();
        return ResponseEntity.ok(categoryQueryResponseMapper.toReadCategoryResponse(categoryHierarchy));
    }
}
