package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ReadProductListResponse(List<ReadProductDetailResponse> products) {
    public ReadProductListResponse {
        products = products == null ? List.of() : products;
    }
}
