package com.project.young.productservice.web.publicapi.controller;

import com.project.young.productservice.application.dto.query.PublicProductFacetType;
import com.project.young.productservice.application.dto.result.PublicProductFacetResult;
import com.project.young.productservice.application.service.PublicProductFacetQueryService;
import com.project.young.productservice.web.publicapi.dto.PublicProductFacetRequest;
import com.project.young.productservice.web.publicapi.dto.PublicProductFacetResponse;
import com.project.young.productservice.web.publicapi.mapper.PublicProductFacetRequestMapper;
import com.project.young.productservice.web.publicapi.mapper.PublicProductFacetResponseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Storefront facet read API. Gateway path: {@code /api/v1/product_service/public/products/facets}.
 */
@RestController
@RequestMapping("public/products/facets")
@Slf4j
public class PublicProductFacetQueryController {

    private final PublicProductFacetQueryService publicProductFacetQueryService;
    private final PublicProductFacetRequestMapper publicProductFacetRequestMapper;
    private final PublicProductFacetResponseMapper publicProductFacetResponseMapper;

    public PublicProductFacetQueryController(
            PublicProductFacetQueryService publicProductFacetQueryService,
            PublicProductFacetRequestMapper publicProductFacetRequestMapper,
            PublicProductFacetResponseMapper publicProductFacetResponseMapper
    ) {
        this.publicProductFacetQueryService = publicProductFacetQueryService;
        this.publicProductFacetRequestMapper = publicProductFacetRequestMapper;
        this.publicProductFacetResponseMapper = publicProductFacetResponseMapper;
    }

    @GetMapping
    public ResponseEntity<PublicProductFacetResponse> getFacets(
            @RequestParam(name = "categoryId") long categoryId,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "brands", required = false) List<String> brands,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "facet", required = false) List<PublicProductFacetType> facets
    ) {
        log.info(
                "REST request to get public product facets: categoryId={}, q={}, brands={}, minPrice={}, maxPrice={}, facets={}",
                categoryId, q, brands, minPrice, maxPrice, facets
        );

        PublicProductFacetRequest request = new PublicProductFacetRequest(
                categoryId,
                q,
                brands,
                minPrice,
                maxPrice,
                facets
        );

        PublicProductFacetResult result = publicProductFacetQueryService.getFacets(
                publicProductFacetRequestMapper.toQuery(request)
        );

        return ResponseEntity.ok(publicProductFacetResponseMapper.toResponse(result));
    }
}
