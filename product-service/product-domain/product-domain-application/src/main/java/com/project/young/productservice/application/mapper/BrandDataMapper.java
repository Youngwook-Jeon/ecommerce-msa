package com.project.young.productservice.application.mapper;

import com.project.young.productservice.application.dto.brand.*;
import com.project.young.productservice.domain.entity.Brand;
import org.springframework.stereotype.Component;

@Component
public class BrandDataMapper {

    public Brand toBrand(CreateBrandCommand command) {
        return Brand.builder()
                .name(command.getName())
                .logoUrl(command.getLogoUrl())
                .status(command.getStatus())
                .build();
    }

    public CreateBrandResponse toCreateBrandResponse(Brand brand, String message) {
        return CreateBrandResponse.builder()
                .brandId(brand.getId().getValue().toString())
                .name(brand.getName())
                .message(message)
                .build();
    }

    public UpdateBrandResponse toUpdateBrandResponse(Brand brand, String message) {
        return UpdateBrandResponse.builder()
                .brandId(brand.getId().getValue().toString())
                .name(brand.getName())
                .message(message)
                .build();
    }

    public DeleteBrandResponse toDeleteBrandResponse(Brand brand, String message) {
        return DeleteBrandResponse.builder()
                .brandId(brand.getId().getValue().toString())
                .name(brand.getName())
                .message(message)
                .build();
    }
}