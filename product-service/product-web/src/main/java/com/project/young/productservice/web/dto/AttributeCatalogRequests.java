package com.project.young.productservice.web.dto;

import com.project.young.productservice.domain.valueobject.AttributeDataType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class AttributeCatalogRequests {
    private AttributeCatalogRequests() {
    }

    public record Definition(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,99}", message = "Invalid attribute code") String code,
            @NotBlank @Size(max = 100) String displayName,
            @NotNull AttributeDataType dataType,
            @Size(max = 20) String canonicalUnit) {
    }

    public record Value(@NotBlank @Size(max = 100) String displayName, String textValue,
                        BigDecimal numericValue, Boolean booleanValue, LocalDate dateValue) {
    }

    public record Rename(@NotBlank @Size(max = 100) String displayName) {
    }

    public record Policy(@NotNull Boolean enabled, @NotNull Boolean required, @NotNull Boolean optionAllowed,
                         @NotNull Boolean filterable, @NotNull @Min(0) Integer displayOrder) {
    }
}
