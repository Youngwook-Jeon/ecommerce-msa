package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.web.dto.ReadCategoryOptionGroupQueryResponse;
import org.springframework.stereotype.Component;

@Component
public class CategoryOptionGroupQueryResponseMapper {
    public ReadCategoryOptionGroupQueryResponse toResponse(ReadCategoryOptionGroupView view) {
        return new ReadCategoryOptionGroupQueryResponse(view.id(), view.name(), view.displayName(),
                view.attributeId(), view.attributeCode(), view.attributeDisplayName(), view.attributeDataType().name(),
                view.canonicalUnit(), view.attributeRequired(), view.displayOrder());
    }
}
