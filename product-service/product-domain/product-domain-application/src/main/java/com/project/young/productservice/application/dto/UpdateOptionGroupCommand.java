package com.project.young.productservice.application.dto;

import com.project.young.productservice.domain.valueobject.OptionStatus;
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
public class UpdateOptionGroupCommand {

    @NotBlank(message = "Name cannot be blank.")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
    private String name;

    @NotBlank(message = "DisplayName cannot be blank.")
    @Size(min = 2, max = 100, message = "DisplayName must be between 2 and 100 characters.")
    private String displayName;

    @NotNull(message = "OptionStatus must not be null.")
    private OptionStatus status;
}
