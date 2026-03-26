package com.project.young.productservice.application.dto.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddProductOptionGroupCommand {

    @NotNull(message = "OptionGroup id must not be null.")
    private UUID optionGroupId;

    @Positive(message = "Step order must be greater than zero.")
    private int stepOrder;

    @Builder.Default
    private boolean required = true;

    @Valid
    @NotEmpty(message = "Product option values must not be empty.")
    private List<AddProductOptionValueCommand> optionValues;
}
