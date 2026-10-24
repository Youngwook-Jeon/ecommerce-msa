package com.project.young.productservice.web.dto;

import java.util.List;

public record ReadCategoryOptionGroupValuesQueryResponse(
        ReadCategoryOptionGroupQueryResponse group,
        List<ReadCategoryOptionValueQueryResponse> optionValues
) {
}
