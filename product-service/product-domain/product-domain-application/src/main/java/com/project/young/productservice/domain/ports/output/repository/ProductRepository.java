package com.project.young.productservice.domain.ports.output.repository;

import com.project.young.productservice.domain.entity.Product;

import java.util.Optional;

public interface ProductRepository {

    Product createProduct(Product product);

    Optional<Product> findByProductName(String productName);
}
