package com.project.young.productservice.web.controller;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.application.service.ProductQueryService;
import com.project.young.productservice.web.dto.ReadProductDetailResponse;
import com.project.young.productservice.web.dto.ReadProductListResponse;
import com.project.young.productservice.web.mapper.ProductQueryResponseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("queries/products")
@Slf4j
public class ProductQueryController {

    private final ProductQueryService productQueryService;
    private final ProductQueryResponseMapper productQueryResponseMapper;

    public ProductQueryController(ProductQueryService productQueryService,
                                  ProductQueryResponseMapper productQueryResponseMapper) {
        this.productQueryService = productQueryService;
        this.productQueryResponseMapper = productQueryResponseMapper;
    }

    @GetMapping
    public ResponseEntity<ReadProductListResponse> getAllVisibleProducts() {
        log.info("REST request to get all visible products");
        List<ReadProductView> products = productQueryService.getAllVisibleProducts();
        return ResponseEntity.ok(productQueryResponseMapper.toReadProductListResponse(products));
    }

    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<ReadProductListResponse> getVisibleProductsByCategory(@PathVariable("categoryId") Long categoryId) {
        log.info("REST request to get visible products for categoryId={}", categoryId);
        List<ReadProductView> products =
                productQueryService.getVisibleProductsByCategory(new CategoryId(categoryId));
        return ResponseEntity.ok(productQueryResponseMapper.toReadProductListResponse(products));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ReadProductDetailResponse> getVisibleProductDetail(@PathVariable("productId") UUID productId) {
        log.info("REST request to get visible product detail for productId={}", productId);
        ReadProductDetailView productView = productQueryService.getVisibleProductDetail(new ProductId(productId));
        return ResponseEntity.ok(productQueryResponseMapper.toReadProductDetailResponse(productView));
    }

}
