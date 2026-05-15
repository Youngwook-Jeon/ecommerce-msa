package com.project.young.productservice.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProductOptionGroupVisualRequest {
    @NotNull
    private Boolean drivesVariantImages;
}
