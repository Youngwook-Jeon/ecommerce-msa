package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.command.AddProductOptionGroupCommand;
import com.project.young.productservice.application.dto.command.AddProductOptionValuesCommand;
import com.project.young.productservice.application.dto.command.AddProductVariantsCommand;
import com.project.young.productservice.application.dto.command.ChangeProductOptionGroupStepOrderCommand;
import com.project.young.productservice.application.dto.command.ReorderProductOptionGroupsCommand;
import com.project.young.productservice.application.dto.command.UpdateProductOptionGroupVisualCommand;
import com.project.young.productservice.application.dto.command.UpdateProductVariantCommand;
import com.project.young.productservice.application.service.ProductApplicationService;
import com.project.young.productservice.application.service.ProductOptionGroupVisualApplicationService;
import com.project.young.productservice.web.dto.*;
import com.project.young.productservice.web.mapper.ProductResponseMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ProductOptionGroupVisualApplicationService productOptionGroupVisualApplicationService;
    private final ProductResponseMapper productResponseMapper;

    public AdminProductCompositionController(ProductApplicationService productApplicationService,
                                             ProductOptionGroupVisualApplicationService productOptionGroupVisualApplicationService,
                                             ProductResponseMapper productResponseMapper) {
        this.productApplicationService = productApplicationService;
        this.productOptionGroupVisualApplicationService = productOptionGroupVisualApplicationService;
        this.productResponseMapper = productResponseMapper;
    }

    /**
     * Adds a group to a DRAFT product only. The product category must explicitly enable this attribute for options.
     * Dictionary groups/values and their attribute definition must be active and canonically linked.
     * Invalid assignments return 400; rejected writes do not change the catalog version or emit catalog events.
     */
    @PostMapping("/{productId}/option-groups")
    public ResponseEntity<AddProductOptionGroupResponse> addOptionGroup(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody AddProductOptionGroupCommand command,
            @RequestParam("expectedVersion") @Positive long expectedVersion
    ) {
        log.info("REST request to add option group to productId={}", productId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productResponseMapper.toAddProductOptionGroupResponse(
                        productApplicationService.addProductOptionGroup(productId, command, expectedVersion)));
    }

    /**
     * Adds values to a non-deleted group owned by this product, including on ACTIVE products.
     * Revalidates current category policy, dictionary status and canonical attribute ownership on every addition.
     * Historical assignments are retained when policies change; they do not authorize new assignments.
     */
    @PostMapping("/{productId}/option-groups/{productOptionGroupId}/option-values")
    public ResponseEntity<AddProductOptionValuesToGroupResponse> addOptionValues(
            @PathVariable("productId") UUID productId,
            @PathVariable("productOptionGroupId") UUID productOptionGroupId,
            @Valid @RequestBody AddProductOptionValuesCommand command,
            @RequestParam("expectedVersion") @Positive long expectedVersion
    ) {
        log.info("REST request to add option values to productId={}, productOptionGroupId={}, count={}",
                productId,
                productOptionGroupId,
                command.getOptionValues() != null ? command.getOptionValues().size() : 0
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AddProductOptionValuesToGroupResponse.builder()
                        .optionValues(
                                productApplicationService.addProductOptionValues(productId, productOptionGroupId, command, expectedVersion)
                                        .stream()
                                        .map(productResponseMapper::toAddProductOptionValueToGroupResponse)
                                        .collect(Collectors.toList())
                        )
                        .build());
    }

    @DeleteMapping("/{productId}/option-groups/{productOptionGroupId}")
    public ResponseEntity<DeleteProductOptionGroupResponse> deleteOptionGroup(
            @PathVariable("productId") UUID productId,
            @PathVariable("productOptionGroupId") UUID productOptionGroupId,
            @RequestParam("expectedVersion") @Positive long expectedVersion
    ) {
        log.info("REST request to soft-delete option group. productId={}, productOptionGroupId={}",
                productId, productOptionGroupId);
        return ResponseEntity.ok(
                productResponseMapper.toDeleteProductOptionGroupResponse(
                        productApplicationService.deleteProductOptionGroup(productId, productOptionGroupId, expectedVersion)
                )
        );
    }

    @DeleteMapping("/{productId}/option-values/{productOptionValueId}")
    public ResponseEntity<DeleteProductOptionValueResponse> deleteOptionValue(
            @PathVariable("productId") UUID productId,
            @PathVariable("productOptionValueId") UUID productOptionValueId,
            @RequestParam("expectedVersion") @Positive long expectedVersion
    ) {
        log.info("REST request to soft-delete option value. productId={}, productOptionValueId={}",
                productId, productOptionValueId);
        return ResponseEntity.ok(
                productResponseMapper.toDeleteProductOptionValueResponse(
                        productApplicationService.deleteProductOptionValue(productId, productOptionValueId, expectedVersion)
                )
        );
    }

    @PatchMapping("/{productId}/option-groups/{productOptionGroupId}/step-order")
    public ResponseEntity<ChangeProductOptionGroupStepOrderResponse> changeOptionGroupStepOrder(
            @PathVariable("productId") UUID productId,
            @PathVariable("productOptionGroupId") UUID productOptionGroupId,
            @Valid @RequestBody ChangeProductOptionGroupStepOrderCommand command,
            @RequestParam("expectedVersion") @Positive long expectedVersion
    ) {
        return ResponseEntity.ok(
                productResponseMapper.toChangeProductOptionGroupStepOrderResponse(
                        productApplicationService.changeProductOptionGroupStepOrder(
                                productId, productOptionGroupId, command, expectedVersion)
                )
        );
    }

    @PatchMapping("/{productId}/option-groups/reorder")
    public ResponseEntity<ReorderProductOptionGroupsResponse> reorderOptionGroups(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody ReorderProductOptionGroupsCommand command,
            @RequestParam("expectedVersion") @Positive long expectedVersion
    ) {
        return ResponseEntity.ok(
                productResponseMapper.toReorderProductOptionGroupsResponse(
                        productApplicationService.reorderProductOptionGroups(productId, command, expectedVersion)
                )
        );
    }

    @PatchMapping("/{productId}/option-groups/{productOptionGroupId}/visual")
    public ResponseEntity<UpdateProductOptionGroupVisualResponse> updateOptionGroupVisual(
            @PathVariable("productId") UUID productId,
            @PathVariable("productOptionGroupId") UUID productOptionGroupId,
            @Valid @RequestBody UpdateProductOptionGroupVisualRequest request,
            @RequestParam("expectedVersion") @Positive long expectedVersion
    ) {
        log.info("REST update visual option group productId={}, productOptionGroupId={}, drives={}",
                productId, productOptionGroupId, request.getDrivesVariantImages());
        return ResponseEntity.ok(
                productResponseMapper.toUpdateProductOptionGroupVisualResponse(
                        productOptionGroupVisualApplicationService.updateVisualFlag(
                                productId,
                                productOptionGroupId,
                                UpdateProductOptionGroupVisualCommand.builder()
                                        .drivesVariantImages(Boolean.TRUE.equals(request.getDrivesVariantImages()))
                                        .build(), expectedVersion)
                )
        );
    }

    /**
     * 선택한 {@code productOptionValueId} 조합들로 변형(SKU)을 생성한다. SKU는 서버에서 부여한다.
     */
    @PostMapping("/{productId}/variants")
    public ResponseEntity<AddProductVariantsResponse> addVariants(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody AddProductVariantsCommand command,
            @RequestParam("expectedVersion") @Positive long expectedVersion
    ) {
        log.info("REST request to add variants to productId={}, variantsCount={}",
                productId,
                command.getVariants() != null ? command.getVariants().size() : 0
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AddProductVariantsResponse.builder()
                        .variants(
                                productApplicationService.addProductVariants(productId, command, expectedVersion)
                                        .stream()
                                        .map(productResponseMapper::toAddProductVariantResponse)
                                        .collect(Collectors.toList())
                        )
                        .build());
    }

    @PatchMapping("/{productId}/variants/{productVariantId}")
    public ResponseEntity<UpdateProductVariantResponse> updateVariant(
            @PathVariable("productId") UUID productId,
            @PathVariable("productVariantId") UUID productVariantId,
            @Valid @RequestBody UpdateProductVariantCommand command,
            @RequestParam("expectedVersion") @Positive long expectedVersion
    ) {
        log.info("REST request to update variant. productId={}, productVariantId={}", productId, productVariantId);
        return ResponseEntity.ok(
                productResponseMapper.toUpdateProductVariantResponse(
                        productApplicationService.updateProductVariant(productId, productVariantId, command, expectedVersion)
                )
        );
    }

    @DeleteMapping("/{productId}/variants/{productVariantId}")
    public ResponseEntity<DeleteProductVariantResponse> deleteVariant(
            @PathVariable("productId") UUID productId,
            @PathVariable("productVariantId") UUID productVariantId,
            @RequestParam("expectedVersion") @Positive long expectedVersion
    ) {
        log.info("REST request to soft-delete variant. productId={}, productVariantId={}", productId, productVariantId);
        return ResponseEntity.ok(
                productResponseMapper.toDeleteProductVariantResponse(
                        productApplicationService.deleteProductVariant(productId, productVariantId, expectedVersion)
                )
        );
    }
}
