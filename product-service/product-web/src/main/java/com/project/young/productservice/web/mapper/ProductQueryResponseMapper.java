package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
import com.project.young.productservice.web.dto.ReadProductDetailResponse;
import com.project.young.productservice.web.dto.ReadProductListResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class ProductQueryResponseMapper {

    private final ProductStatusWebConverter productStatusWebConverter;
    private final ConditionTypeWebConverter conditionTypeWebConverter;

    public ProductQueryResponseMapper(ProductStatusWebConverter productStatusWebConverter,
                                      ConditionTypeWebConverter conditionTypeWebConverter) {
        this.productStatusWebConverter = productStatusWebConverter;
        this.conditionTypeWebConverter = conditionTypeWebConverter;
    }

    public ReadProductDetailResponse toReadProductDetailResponse(ReadProductView readProductView) {
        Objects.requireNonNull(readProductView, "ReadProductView is null");
        return ReadProductDetailResponse.builder()
                .id(readProductView.id())
                .categoryId(readProductView.categoryId())
                .name(readProductView.name())
                .description(readProductView.description())
                .basePrice(readProductView.basePrice())
                .brand(readProductView.brand())
                .mainImageUrl(readProductView.mainImageUrl())
                .conditionType(conditionTypeWebConverter.toStringValue(readProductView.conditionType()))
                .status(productStatusWebConverter.toStringValue(readProductView.status()))
                .build();
    }

    public ReadProductListResponse toReadProductListResponse(List<ReadProductView> readProductViews) {
        return ReadProductListResponse.builder()
                .products(readProductViews.stream()
                        .map(this::toReadProductDetailResponse)
                        .toList())
                .build();
    }
}
