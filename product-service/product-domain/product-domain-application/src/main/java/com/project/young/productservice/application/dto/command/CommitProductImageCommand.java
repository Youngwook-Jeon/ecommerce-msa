package com.project.young.productservice.application.dto.command;

import com.project.young.productservice.domain.valueobject.ProductImageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommitProductImageCommand {

    @NotBlank
    @Size(max = 512)
    private String objectKey;

    @NotBlank
    @Size(max = 100)
    private String contentType;

    @Positive
    private long fileSize;

    @NotNull
    private ProductImageRole role;

    private int sortOrder;
}
