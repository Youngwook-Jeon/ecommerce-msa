package com.project.young.productservice.application.dto.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddProductVariantsCommand {

    @NotNull(message = "Variants must not be null.")
    @NotEmpty(message = "At least one variant is required.")
    @Valid
    private List<@NotNull(message = "Variant command must not be null.") AddProductVariantCommand> variants;
}

