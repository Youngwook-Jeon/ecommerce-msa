package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.dto.condition.ProductSearchCriteria;
import com.project.young.productservice.application.dto.result.ProductSearchResult;

public interface ProductSearchPort {

    /** Return complete results only; infrastructure failures must raise ProductSearchUnavailableException. */
    ProductSearchResult search(ProductSearchCriteria criteria);
}
