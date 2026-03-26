package com.project.young.productservice.application.dto.condition;

import com.project.young.productservice.domain.valueobject.ProductStatus;

public record AdminProductSearchCondition(
        Long categoryId,          // null이면 카테고리 필터 없음
        Boolean includeOrphans,   // null 또는 false면 카테고리 있는 것들만, true면 카테고리 없는 제품도 포함
        ProductStatus status,     // null이면 상태 필터 없음
        String brand,             // null/blank이면 필터 없음
        String keyword            // 이름/설명 검색용
) {
    public boolean includeOrphansOrDefault() {
        return Boolean.TRUE.equals(includeOrphans);
    }

    public String normalizedBrand() {
        return (brand == null || brand.isBlank()) ? null : brand;
    }

    public String normalizedKeyword() {
        return (keyword == null || keyword.isBlank()) ? null : keyword;
    }
}