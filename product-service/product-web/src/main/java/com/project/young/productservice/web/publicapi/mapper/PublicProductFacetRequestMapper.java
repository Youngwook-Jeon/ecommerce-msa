package com.project.young.productservice.web.publicapi.mapper;

import com.project.young.productservice.application.dto.query.PublicProductFacetQuery;
import com.project.young.productservice.web.publicapi.dto.PublicProductFacetRequest;
import org.springframework.stereotype.Component;

@Component
public class PublicProductFacetRequestMapper {

    public PublicProductFacetQuery toQuery(PublicProductFacetRequest request) {
        return new PublicProductFacetQuery(
                request.categoryId(),
                request.q(),
                request.brands(),
                request.minPrice(),
                request.maxPrice(),
                request.facets()
        );
    }
}
