package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.dto.event.ProductCatalogChangedEvent;

public interface ProductCatalogOutboxPort {

    void enqueue(ProductCatalogChangedEvent event);
}
