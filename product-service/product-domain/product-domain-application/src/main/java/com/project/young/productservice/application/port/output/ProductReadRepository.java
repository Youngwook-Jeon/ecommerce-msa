package com.project.young.productservice.application.port.output;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.view.ReadProductView;

import java.util.List;
import java.util.Optional;

public interface ProductReadRepository {

    List<ReadProductView> findAllVisibleProducts();

    List<ReadProductView> findVisibleByCategoryId(CategoryId categoryId);

    Optional<ReadProductView> findVisibleById(ProductId productId);

    // TODO: Implement admin product search functionality
//    List<ReadProductView> findAdminProducts(ProductSearchCondition condition);
}