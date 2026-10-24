package com.project.young.productservice.web.dto;

import java.util.UUID;

public record ReadCategoryOptionValueQueryResponse(
        UUID id,
        String value,
        String displayName,
        int sortOrder,
        AttributeCatalogResponses.Value attributeValue
) {
}
