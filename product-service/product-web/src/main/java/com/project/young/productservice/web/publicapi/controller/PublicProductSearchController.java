package com.project.young.productservice.web.publicapi.controller;

import com.project.young.productservice.application.service.ProductSearchService;
import com.project.young.productservice.web.publicapi.dto.PublicProductSearchResponse;
import com.project.young.productservice.web.publicapi.mapper.PublicProductSearchRequestMapper;
import com.project.young.productservice.web.publicapi.mapper.PublicProductSearchResponseMapper;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("public/products/search")
public class PublicProductSearchController {

    private final ProductSearchService productSearchService;
    private final PublicProductSearchRequestMapper requestMapper;
    private final PublicProductSearchResponseMapper responseMapper;

    public PublicProductSearchController(ProductSearchService productSearchService,
                                         PublicProductSearchRequestMapper requestMapper,
                                         PublicProductSearchResponseMapper responseMapper) {
        this.productSearchService = productSearchService;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PublicProductSearchResponse search(@RequestParam MultiValueMap<String, String> parameters) {
        var query = requestMapper.toQuery(requestMapper.fromParameters(parameters));
        return responseMapper.toResponse(productSearchService.search(query));
    }
}
