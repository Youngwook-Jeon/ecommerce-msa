package com.project.young.productservice.web.dto;

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
public class ProductImageCommitRequest {

    @NotBlank
    @Size(max = 512)
    private String objectKey;

    @NotBlank
    @Size(max = 100)
    private String contentType;

    @Positive
    private long fileSize;

    @NotBlank
    @Size(max = 20)
    private String role;

    private int sortOrder;
}
