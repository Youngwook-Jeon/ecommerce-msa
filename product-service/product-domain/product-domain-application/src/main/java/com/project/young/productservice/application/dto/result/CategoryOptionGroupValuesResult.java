package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionValueView;

import java.util.List;

public record CategoryOptionGroupValuesResult(
        ReadCategoryOptionGroupView group,
        List<ReadCategoryOptionValueView> optionValues
) {
    public CategoryOptionGroupValuesResult {
        optionValues = List.copyOf(optionValues);
    }
}
