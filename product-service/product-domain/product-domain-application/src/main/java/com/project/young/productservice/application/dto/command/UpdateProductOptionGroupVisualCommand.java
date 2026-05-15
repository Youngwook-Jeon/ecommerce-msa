package com.project.young.productservice.application.dto.command;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdateProductOptionGroupVisualCommand {
    private final boolean drivesVariantImages;
}
