package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.result.CategoryOptionGroupValuesResult;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionValueView;
import com.project.young.productservice.web.dto.AttributeCatalogResponses;
import com.project.young.productservice.web.dto.ReadCategoryOptionGroupQueryResponse;
import com.project.young.productservice.web.dto.ReadCategoryOptionGroupValuesQueryResponse;
import com.project.young.productservice.web.dto.ReadCategoryOptionValueQueryResponse;
import org.springframework.stereotype.Component;

@Component
public class CategoryOptionGroupQueryResponseMapper {
    public ReadCategoryOptionGroupQueryResponse toResponse(ReadCategoryOptionGroupView view) {
        return new ReadCategoryOptionGroupQueryResponse(view.id(), view.name(), view.displayName(),
                view.attributeId(), view.attributeCode(), view.attributeDisplayName(), view.attributeDataType().name(),
                view.canonicalUnit(), view.attributeRequired(), view.displayOrder());
    }

    public ReadCategoryOptionGroupValuesQueryResponse toValuesResponse(CategoryOptionGroupValuesResult result) {
        return new ReadCategoryOptionGroupValuesQueryResponse(toResponse(result.group()),
                result.optionValues().stream().map(this::toValueResponse).toList());
    }

    private ReadCategoryOptionValueQueryResponse toValueResponse(ReadCategoryOptionValueView view) {
        var attributeValue = new AttributeCatalogResponses.Value(view.attributeValueId(), view.attributeId(),
                view.attributeDataType().name(), view.attributeValueDisplayName(), view.textValue(), view.numericValue(),
                view.booleanValue(), view.dateValue());
        return new ReadCategoryOptionValueQueryResponse(view.id(), view.value(), view.displayName(), view.sortOrder(), attributeValue);
    }
}
