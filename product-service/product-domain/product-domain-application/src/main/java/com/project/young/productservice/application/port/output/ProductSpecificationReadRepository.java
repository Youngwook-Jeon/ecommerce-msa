package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.dto.result.ProductSpecificationsResult;

import java.util.UUID;

public interface ProductSpecificationReadRepository {
    ProductSpecificationsResult findByProductId(UUID productId);
}
