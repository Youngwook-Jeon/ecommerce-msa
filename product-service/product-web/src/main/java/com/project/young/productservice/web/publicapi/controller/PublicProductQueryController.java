package com.project.young.productservice.web.publicapi.controller;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.query.PublicProductListQuery;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.dto.result.PublicProductListPageResult;
import com.project.young.productservice.application.service.PublicProductQueryService;
import com.project.young.productservice.web.publicapi.dto.PublicProductDetailResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductPageResponse;
import com.project.young.productservice.web.publicapi.mapper.PublicProductQueryResponseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Storefront read API. Gateway path: {@code /api/v1/product_service/public/products}.
 */
@RestController
@RequestMapping("public/products")
@Slf4j
public class PublicProductQueryController {

    private final PublicProductQueryService publicProductQueryService;
    private final PublicProductQueryResponseMapper publicProductQueryResponseMapper;

    public PublicProductQueryController(
            PublicProductQueryService publicProductQueryService,
            PublicProductQueryResponseMapper publicProductQueryResponseMapper
    ) {
        this.publicProductQueryService = publicProductQueryService;
        this.publicProductQueryResponseMapper = publicProductQueryResponseMapper;
    }

    @GetMapping
    public ResponseEntity<PublicProductPageResponse> listProducts(
            @RequestParam(name = "categoryId", required = true) long categoryId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "24") int size,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "brands", required = false) List<String> brands,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice
    ) {
        List<String> normalizedBrands = brands == null ? List.of() : List.copyOf(brands);

        log.info(
                "REST request to list public products: categoryId={}, page={}, size={}, q={}, sort={}, brands={}, minPrice={}, maxPrice={}",
                categoryId, page, size, q, sort, normalizedBrands, minPrice, maxPrice
        );

        PublicProductListPageResult result = publicProductQueryService.listProductsByCategory(
                new PublicProductListQuery(categoryId, page, size, q, sort, normalizedBrands, minPrice, maxPrice)
        );

        return ResponseEntity.ok(publicProductQueryResponseMapper.toPublicProductPageResponse(result));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<PublicProductDetailResponse> getProductDetail(@PathVariable("productId") UUID productId) {
        log.info("REST request to get public product detail: productId={}", productId);
        ReadProductDetailView detail = publicProductQueryService.getStorefrontProductDetail(new ProductId(productId));
        return ResponseEntity.ok(publicProductQueryResponseMapper.toPublicProductDetailResponse(detail));
    }
}
