package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.command.AddProductOptionGroupCommand;
import com.project.young.productservice.application.dto.command.AddProductOptionValueCommand;
import com.project.young.productservice.application.dto.command.AddProductVariantCommand;
import com.project.young.productservice.application.service.ProductApplicationService;
import com.project.young.productservice.web.dto.AddProductOptionGroupResponse;
import com.project.young.productservice.web.dto.AddProductOptionValueToGroupResponse;
import com.project.young.productservice.web.dto.AddProductVariantResponse;
import com.project.young.productservice.web.mapper.ProductResponseMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 어드민 상품 구성 플로우: 원형(DRAFT) 생성은 {@link ProductController#create} 이후,
 * 옵션 그룹(초기 옵션 값 포함) → 추가 옵션 값 → SKU 변형 순으로 리소스를 붙인다.
 */
@Slf4j
@RestController
@RequestMapping("admin/products")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminProductCompositionController {

    private final ProductApplicationService productApplicationService;
    private final ProductResponseMapper productResponseMapper;

    public AdminProductCompositionController(ProductApplicationService productApplicationService,
                                             ProductResponseMapper productResponseMapper) {
        this.productApplicationService = productApplicationService;
        this.productResponseMapper = productResponseMapper;
    }

    /**
     * 상품에 글로벌 옵션 그룹을 연결한다. 요청 본문에 해당 그룹에 속할 옵션 값이 최소 1개 이상 포함되어야 한다.
     */
    @PostMapping("/{productId}/option-groups")
    public ResponseEntity<AddProductOptionGroupResponse> addOptionGroup(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody AddProductOptionGroupCommand command
    ) {
        log.info("REST request to add option group to productId={}", productId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productResponseMapper.toAddProductOptionGroupResponse(
                        productApplicationService.addProductOptionGroup(productId, command)));
    }

    /**
     * 이미 상품에 붙은 옵션 그룹({@code productOptionGroupId})에 옵션 값(글로벌 optionValue + 가격 델타)을 추가한다.
     */
    @PostMapping("/{productId}/option-groups/{productOptionGroupId}/option-values")
    public ResponseEntity<AddProductOptionValueToGroupResponse> addOptionValue(
            @PathVariable("productId") UUID productId,
            @PathVariable("productOptionGroupId") UUID productOptionGroupId,
            @Valid @RequestBody AddProductOptionValueCommand command
    ) {
        log.info("REST request to add option value to productId={}, productOptionGroupId={}", productId, productOptionGroupId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productResponseMapper.toAddProductOptionValueToGroupResponse(
                        productApplicationService.addProductOptionValue(productId, productOptionGroupId, command)));
    }

    /**
     * 선택한 {@code productOptionValueId} 조합으로 변형(SKU)을 생성한다. SKU는 서버에서 부여한다.
     */
    @PostMapping("/{productId}/variants")
    public ResponseEntity<AddProductVariantResponse> addVariant(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody AddProductVariantCommand command
    ) {
        log.info("REST request to add variant to productId={}", productId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productResponseMapper.toAddProductVariantResponse(
                        productApplicationService.addProductVariant(productId, command)));
    }
}
