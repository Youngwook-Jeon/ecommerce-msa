package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.ProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import lombok.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductReadRepository productReadRepository;

    public ProductQueryService(ProductReadRepository productReadRepository) {
        this.productReadRepository = productReadRepository;
    }

    public List<ReadProductView> getVisibleProductsByCategory(@NonNull CategoryId categoryId) {
        return productReadRepository.findVisibleByCategoryId(categoryId);
    }

    public ReadProductView getVisibleProductDetail(@NonNull ProductId productId) {
        return productReadRepository.findVisibleById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found or not visible: " + productId));
    }
}