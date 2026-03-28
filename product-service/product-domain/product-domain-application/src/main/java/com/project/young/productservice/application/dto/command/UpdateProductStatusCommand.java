package com.project.young.productservice.application.dto.command;

import com.project.young.productservice.domain.valueobject.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProductStatusCommand {

    @NotNull(message = "Product status must not be null.")
    private ProductStatus status;
}
