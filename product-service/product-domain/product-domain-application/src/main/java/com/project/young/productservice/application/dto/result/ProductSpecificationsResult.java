package com.project.young.productservice.application.dto.result;

import java.util.List;
import java.util.UUID;

public record ProductSpecificationsResult(UUID productId, long version, List<ProductSpecificationResult> specifications) {
    public ProductSpecificationsResult {
        specifications = List.copyOf(specifications);
    }
}
