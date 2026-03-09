package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.AdminProductSearchCondition;
import com.project.young.productservice.application.port.output.AdminProductReadRepository;
import com.project.young.productservice.application.service.AdminProductQueryService;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.web.dto.AdminProductPageResponse;
import com.project.young.productservice.web.mapper.AdminProductQueryResponseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("admin/queries/products")
@Slf4j
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminProductQueryController {

    private final AdminProductQueryService adminProductQueryService;
    private final AdminProductQueryResponseMapper adminProductQueryResponseMapper;

    public AdminProductQueryController(AdminProductQueryService adminProductQueryService,
                                       AdminProductQueryResponseMapper adminProductQueryResponseMapper) {
        this.adminProductQueryService = adminProductQueryService;
        this.adminProductQueryResponseMapper = adminProductQueryResponseMapper;
    }

    @GetMapping
    public ResponseEntity<AdminProductPageResponse> search(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "includeOrphans", required = false, defaultValue = "true") boolean includeOrphans,
            @RequestParam(name = "status", required = false) ProductStatus status,
            @RequestParam(name = "brand", required = false) String brand,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        log.info("Admin search products: page={}, size={}, sort={}, categoryId={}, includeOrphans={}, status={}, brand={}, keyword={}",
                page, size, sort, categoryId, includeOrphans, status, brand, keyword);

        String sortProperty = parseSortProperty(sort);
        boolean ascending = parseSortDirection(sort);

        AdminProductSearchCondition condition = new AdminProductSearchCondition(
                categoryId,
                includeOrphans,
                status,
                brand,
                keyword
        );

        AdminProductReadRepository.AdminProductSearchResult result =
                adminProductQueryService.search(condition, page, size, sortProperty, ascending);

        AdminProductPageResponse response = adminProductQueryResponseMapper.toAdminProductPageResponse(
                result.content(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );

        return ResponseEntity.ok(response);
    }

    private String parseSortProperty(String sort) {
        if (sort == null || sort.isBlank()) {
            return "createdAt";
        }
        String[] parts = sort.split(",");
        return parts[0];
    }

    private boolean parseSortDirection(String sort) {
        if (sort == null || sort.isBlank()) {
            return false; // desc 기본
        }
        String[] parts = sort.split(",");
        if (parts.length < 2) {
            return false;
        }
        return "asc".equalsIgnoreCase(parts[1]);
    }
}

