package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.command.CreateProductCommand;
import com.project.young.productservice.application.dto.command.UpdateProductCommand;
import com.project.young.productservice.application.dto.command.UpdateProductStatusCommand;
import com.project.young.productservice.application.service.ProductApplicationService;
import com.project.young.productservice.web.dto.CreateProductResponse;
import com.project.young.productservice.web.dto.DeleteProductResponse;
import com.project.young.productservice.web.dto.UpdateProductResponse;
import com.project.young.productservice.web.mapper.ProductResponseMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 상품 루트 CRUD. 신규 상품은 항상 DRAFT로 생성되며,
 * 상태 전환은 {@code PATCH /products/{id}/status} 로만 수행한다.
 * 옵션 그룹(최소 1개 옵션 값 동반)·추가 값·변형은 {@link AdminProductCompositionController} 의 {@code /admin/products/...} API로 이어 붙인다.
 */
@Slf4j
@RestController
@RequestMapping("products")
public class ProductController {

    private final ProductApplicationService productApplicationService;
    private final ProductResponseMapper productResponseMapper;

    public ProductController(ProductApplicationService productApplicationService,
                             ProductResponseMapper productResponseMapper) {
        this.productApplicationService = productApplicationService;
        this.productResponseMapper = productResponseMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<CreateProductResponse> create(@Valid @RequestBody CreateProductCommand command) {
        log.info("A post request to create Product: {}", command.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productResponseMapper.toCreateProductResponse(
                        productApplicationService.createProduct(command)));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UpdateProductResponse> update(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody UpdateProductCommand command) {
        log.info("A put request to update Product with id: {}, command: {}", productId, command.getName());
        return ResponseEntity.ok(productResponseMapper.toUpdateProductResponse(
                productApplicationService.updateProduct(productId, command)));
    }

    @PatchMapping("/{productId}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UpdateProductResponse> updateStatus(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody UpdateProductStatusCommand command) {
        log.info("A patch request to update Product status with id: {}, status: {}", productId, command.getStatus());
        return ResponseEntity.ok(productResponseMapper.toUpdateProductStatusResponse(
                productApplicationService.updateProductStatus(productId, command)));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<DeleteProductResponse> delete(@PathVariable("productId") UUID productId) {
        log.info("A delete request to delete Product with id: {}", productId);
        return ResponseEntity.ok(productResponseMapper.toDeleteProductResponse(
                productApplicationService.deleteProduct(productId)));
    }
}

