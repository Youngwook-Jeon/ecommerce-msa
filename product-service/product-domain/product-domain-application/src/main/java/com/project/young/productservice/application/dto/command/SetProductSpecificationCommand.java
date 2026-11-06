package com.project.young.productservice.application.dto.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SetProductSpecificationCommand(@NotNull UUID attributeValueId) {
}
