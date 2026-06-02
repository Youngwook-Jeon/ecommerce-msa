package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record PublicProductOptionGroupResponse(
        UUID productOptionGroupId,
        UUID optionGroupId,
        String groupKey,
        String displayName,
        double stepOrder,
        boolean required,
        boolean drivesVariantImages,
        List<PublicProductOptionValueResponse> optionValues
) {
    public PublicProductOptionGroupResponse {
        optionValues = optionValues == null ? List.of() : optionValues;
    }
}
