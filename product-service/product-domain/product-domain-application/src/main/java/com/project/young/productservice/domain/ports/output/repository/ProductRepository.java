package com.project.young.productservice.domain.ports.output.repository;

import com.project.young.productservice.domain.entity.Product;

public interface ProductRepository {

    Product createProduct(Product product);
}
