package com.project.young.productservice.application.dto.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddProductOptionValuesCommand {

    @Valid
    @NotEmpty(message = "Option values must not be empty.")
    private List<AddProductOptionValueCommand> optionValues;
}

