package com.project.young.productservice.application.dto.command;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class ReorderProductOptionGroupsCommand {

    @NotEmpty(message = "Reorder target groups must not be empty.")
    private List<@NotNull(message = "Product option group id must not be null.") UUID> orderedProductOptionGroupIds;
}

