package com.project.young.productservice.web.publicapi.controller;

import com.project.young.productservice.application.port.output.view.ReadCartCatalogLineView;
import com.project.young.productservice.application.service.PublicCartCatalogQueryService;
import com.project.young.productservice.web.publicapi.dto.PublicCartCatalogLinesResponse;
import com.project.young.productservice.web.publicapi.dto.PublicCartCatalogLinesSearchRequest;
import com.project.young.productservice.web.publicapi.mapper.PublicCartCatalogQueryResponseMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Storefront cart catalog read API.
 * Gateway path: {@code POST /api/v1/product_service/public/catalog/cart-lines/search}.
 */
@RestController
@RequestMapping("public/catalog")
@Slf4j
public class PublicCartCatalogQueryController {

    private final PublicCartCatalogQueryService publicCartCatalogQueryService;
    private final PublicCartCatalogQueryResponseMapper responseMapper;

    public PublicCartCatalogQueryController(
            PublicCartCatalogQueryService publicCartCatalogQueryService,
            PublicCartCatalogQueryResponseMapper responseMapper
    ) {
        this.publicCartCatalogQueryService = publicCartCatalogQueryService;
        this.responseMapper = responseMapper;
    }

    @PostMapping("/cart-lines/search")
    public ResponseEntity<PublicCartCatalogLinesResponse> searchCartLines(
            @Valid @RequestBody PublicCartCatalogLinesSearchRequest request
    ) {
        log.info("REST request to search cart catalog lines: variantCount={}", request.productVariantIds().size());

        List<ReadCartCatalogLineView> lines = publicCartCatalogQueryService.resolveCartLines(request.productVariantIds());
        return ResponseEntity.ok(responseMapper.toResponse(lines));
    }
}
