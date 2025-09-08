package com.project.young.productservice.application.dto.brand;

import lombok.Builder;

@Builder
public record CreateBrandResponse(
        String brandId,
        String name,
        String message
) {}