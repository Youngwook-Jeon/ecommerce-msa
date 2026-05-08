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
public class ProductImagePresignRequest {

    @NotBlank
    @Size(max = 200)
    private String fileName;

    @NotBlank
    @Size(max = 100)
    private String contentType;

    @Positive
    private long contentLength;

    @NotBlank
    @Size(max = 20)
    private String role;

    private int sortOrder;
}
