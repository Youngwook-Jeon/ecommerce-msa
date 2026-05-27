package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.dto.query.PublicProductFacetQuery;
import com.project.young.productservice.application.dto.result.PublicProductFacetResult;

public interface PublicProductFacetReadRepository {

    PublicProductFacetResult getFacets(PublicProductFacetQuery query);
}
