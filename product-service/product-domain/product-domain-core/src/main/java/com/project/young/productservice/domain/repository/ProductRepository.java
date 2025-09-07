
package com.project.young.productservice.domain.repository;

import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    List<Product> saveAll(List<Product> products);

    Optional<Product> findById(ProductId productId);

    List<Product> findAllById(List<ProductId> productIds);

    List<Product> findByStatus(String status);

    List<Product> findByStatusIn(List<String> statuses);

    List<Product> findByCategoryId(CategoryId categoryId);

    List<Product> findByCategoryIdAndStatus(CategoryId categoryId, String status);

    List<Product> findByCategoryIdIn(List<CategoryId> categoryIds);

    List<Product> findByCategoryIdInAndStatus(List<CategoryId> categoryIds, String status);

    List<Product> findByBrandId(BrandId brandId);

    List<Product> findByBrandIdAndStatus(BrandId brandId, String status);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, ProductId productIdToExclude);

    void deleteById(ProductId productId);

    void updateStatusByIds(String status, List<ProductId> productIds);

    void updateStatusByCategoryId(String status, CategoryId categoryId);

    void updateStatusByCategoryIds(String status, List<CategoryId> categoryIds);

    void updateStatusByBrandId(String status, BrandId brandId);

    void nullifyCategoryReference(CategoryId categoryId);

    void nullifyBrandReference(BrandId brandId);

    long countByStatus(String status);

    long countByCategoryId(CategoryId categoryId);

    long countByBrandId(BrandId brandId);

    long countByCategoryIdAndStatus(CategoryId categoryId, String status);

    long countByBrandIdAndStatus(BrandId brandId, String status);

}