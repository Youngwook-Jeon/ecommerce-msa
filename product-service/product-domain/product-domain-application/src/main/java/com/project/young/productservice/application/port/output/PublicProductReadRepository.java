package com.project.young.productservice.application.port.output;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.condition.PublicProductSearchCondition;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.application.dto.result.PublicProductListPageResult;
import com.project.young.productservice.application.port.output.view.ReadCartCatalogLineView;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicProductReadRepository {

    PublicProductListPageResult search(
            PublicProductSearchCondition condition,
            PublicProductSort sort,
            int page,
            int size
    );

    Optional<ReadProductDetailView> findStorefrontProductDetailById(ProductId productId);

    /**
     * Resolves storefront cart snapshot fields for the given variant ids in one read.
     * Missing or non-visible variants are omitted from the result.
     */
    List<ReadCartCatalogLineView> findCartCatalogLinesByVariantIds(List<UUID> productVariantIds);
}
