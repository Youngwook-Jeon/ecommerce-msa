package com.project.young.productservice.application.dto.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddOptionValuesCommand {

    @NotNull(message = "Option values must not be null.")
    @Size(min = 1, message = "At least one option value is required.")
    @Valid
    private List<AddOptionValueCommand> optionValues;
}

