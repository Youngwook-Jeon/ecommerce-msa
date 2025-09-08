package com.project.young.productservice.application.dto.brand;

import lombok.Builder;

@Builder
public record UpdateBrandResponse(
        String brandId,
        String name,
        String message
) {}