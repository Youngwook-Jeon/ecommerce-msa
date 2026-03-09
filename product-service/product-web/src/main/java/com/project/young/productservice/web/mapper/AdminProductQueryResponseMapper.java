package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
import com.project.young.productservice.web.dto.AdminProductListItemResponse;
import com.project.young.productservice.web.dto.AdminProductPageResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminProductQueryResponseMapper {

    private final ProductStatusWebConverter productStatusWebConverter;
    private final ConditionTypeWebConverter conditionTypeWebConverter;

    public AdminProductQueryResponseMapper(ProductStatusWebConverter productStatusWebConverter,
                                           ConditionTypeWebConverter conditionTypeWebConverter) {
        this.productStatusWebConverter = productStatusWebConverter;
        this.conditionTypeWebConverter = conditionTypeWebConverter;
    }

    public AdminProductPageResponse toAdminProductPageResponse(
            List<ReadProductView> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        List<AdminProductListItemResponse> items = content.stream()
                .map(this::toAdminProductListItemResponse)
                .toList();

        return AdminProductPageResponse.builder()
                .content(items)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    private AdminProductListItemResponse toAdminProductListItemResponse(ReadProductView view) {
        return AdminProductListItemResponse.builder()
                .id(view.id())
                .categoryId(view.categoryId())
                .name(view.name())
                .brand(view.brand())
                .mainImageUrl(view.mainImageUrl())
                .basePrice(view.basePrice())
                .status(productStatusWebConverter.toStringValue(view.status()))
                .conditionType(conditionTypeWebConverter.toStringValue(view.conditionType()))
                .build();
    }
}
