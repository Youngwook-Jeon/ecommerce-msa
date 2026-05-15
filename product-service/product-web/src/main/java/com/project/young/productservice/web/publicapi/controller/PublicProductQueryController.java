package com.project.young.productservice.web.publicapi.controller;

import com.project.young.productservice.web.publicapi.dto.PublicProductPageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Storefront read API. Gateway path: {@code /api/v1/product_service/public/products}.
 */
@RestController
@RequestMapping("public/products")
@Slf4j
public class PublicProductQueryController {

    @GetMapping
    public ResponseEntity<PublicProductPageResponse> listProducts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        log.info("REST request to list public products: page={}, size={}", page, size);
        return ResponseEntity.ok(PublicProductPageResponse.empty(page, size));
    }
}
