package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.dto.AdminProductDetailQuery;
import com.project.young.productservice.application.dto.AdminProductDetailResult;
import com.project.young.productservice.application.dto.AdminProductSearchCondition;
import com.project.young.productservice.application.port.output.view.ReadProductView;

import java.util.List;

public interface AdminProductReadRepository {

    AdminProductDetailResult getProductDetail(AdminProductDetailQuery query);

    AdminProductSearchResult search(AdminProductSearchCondition condition,
                                    int page,
                                    int size,
                                    String sortProperty,
                                    boolean ascending);

    record AdminProductSearchResult(
            List<ReadProductView> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
