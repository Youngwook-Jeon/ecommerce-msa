package com.project.young.productservice.web.publicapi.mapper;

import com.project.young.productservice.application.dto.result.ProductSearchPageResult;
import com.project.young.productservice.web.publicapi.dto.PublicProductSearchFacetResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductSearchResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PublicProductSearchResponseMapper {

    public PublicProductSearchResponse toResponse(ProductSearchPageResult page) {
        var criteria = page.criteria();
        var result = page.result();
        var products = result.content().stream()
                .map(product -> new PublicProductSearchResponse.Product(product.id(),
                        new PublicProductSearchResponse.Category(product.categoryId(), product.categoryName()),
                        product.name(), product.brand(), product.mainImageUrl(), product.basePrice()))
                .toList();
        var brands = result.brands().stream()
                .map(brand -> new PublicProductSearchFacetResponse.Value(brand.value(), brand.label(), brand.count()))
                .toList();
        return new PublicProductSearchResponse(
                new PublicProductSearchResponse.Query(criteria.q(), criteria.sort().apiValue()),
                products, criteria.page(), criteria.size(), result.totalElements(), page.totalPages(),
                List.of(new PublicProductSearchFacetResponse.Terms("brand", "브랜드", brands, result.hasMoreBrands()),
                        new PublicProductSearchFacetResponse.Range("price", "가격", result.minPrice(), result.maxPrice())));
    }
}
