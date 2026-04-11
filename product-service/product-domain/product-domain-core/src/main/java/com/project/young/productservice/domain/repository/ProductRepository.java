package com.project.young.productservice.domain.repository;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.entity.Product;

import java.util.Optional;

public interface ProductRepository {

    void insert(Product product);

    void update(Product product);

    Optional<Product> findById(ProductId productId);

    boolean existsBySku(String sku);
}