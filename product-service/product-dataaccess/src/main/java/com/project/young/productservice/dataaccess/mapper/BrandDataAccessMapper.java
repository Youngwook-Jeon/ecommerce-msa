package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.productservice.dataaccess.entity.BrandEntity;
import com.project.young.productservice.domain.entity.Brand;
import org.springframework.stereotype.Component;

@Component
public class BrandDataAccessMapper {

    public BrandEntity brandToBrandEntity(Brand brand) {
        return BrandEntity.builder()
                .id(brand.getId() != null ? brand.getId().getValue() : null)
                .name(brand.getName())
                .logoUrl(brand.getLogoUrl())
                .status(brand.getStatus())
                .createdAt(brand.getCreatedAt())
                .updatedAt(brand.getUpdatedAt())
                .build();
    }

    public Brand brandEntityToBrand(BrandEntity brandEntity) {
        return Brand.builder()
                .brandId(new BrandId(brandEntity.getId()))
                .name(brandEntity.getName())
                .logoUrl(brandEntity.getLogoUrl())
                .status(brandEntity.getStatus())
                .createdAt(brandEntity.getCreatedAt())
                .updatedAt(brandEntity.getUpdatedAt())
                .build();
    }
}