package com.project.young.productservice.application.dto.command;

import com.project.young.productservice.domain.valueobject.OptionStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateOptionValueCommand {

    @NotBlank(message = "Name cannot be blank.")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
    private String value;

    @NotBlank(message = "DisplayName cannot be blank.")
    @Size(min = 2, max = 100, message = "DisplayName must be between 2 and 100 characters.")
    private String displayName;

    @NotNull(message = "OptionStatus must not be null.")
    private OptionStatus status;

    @NotNull(message = "SortOrder must not be null.")
    @Min(value = 0, message = "SortOrder must be 0 or greater.")
    private Integer sortOrder;
}
