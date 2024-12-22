package com.project.young.productservice.domain;

import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.event.ProductCreatedEvent;

public interface ProductDomainService {

    ProductCreatedEvent initiateProduct(Product product);
}
