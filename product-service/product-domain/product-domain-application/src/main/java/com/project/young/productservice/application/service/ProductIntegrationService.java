package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.domain.service.ProductDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class ProductIntegrationService {

    private final ProductRepository productRepository;
    private final ProductDomainService productDomainService;

    public ProductIntegrationService(ProductRepository productRepository,
                                     ProductDomainService productDomainService) {
        this.productRepository = productRepository;
        this.productDomainService = productDomainService;
    }

    @Transactional
    public void handleCategoryStatusChanged(CategoryId categoryId, String oldStatus, String newStatus) {
        log.info("Integrating category status change: categoryId={}, {} -> {}",
                categoryId.getValue(), oldStatus, newStatus);

        List<Product> affectedProducts = productRepository.findByCategoryId(categoryId);

        if (affectedProducts.isEmpty()) {
            return;
        }

        String targetProductStatus = determineProductStatusBasedOnCategory(newStatus);

        if (targetProductStatus == null) {
            return;
        }

        // 도메인 서비스로 검증
        productDomainService.validateStatusChangeRules(affectedProducts, targetProductStatus);

        // 상태 변경 및 이벤트 발행
        processProductStatusChanges(affectedProducts, targetProductStatus);

        log.info("Integrated category status change for {} products", affectedProducts.size());
    }

    @Transactional
    public void handleCategoryDeleted(List<CategoryId> deletedCategoryIds) {
        log.info("Integrating category deletion: {} categories", deletedCategoryIds.size());

        List<Product> affectedProducts = productRepository.findByCategoryIdIn(deletedCategoryIds);

        if (affectedProducts.isEmpty()) {
            return;
        }

        // 카테고리 참조 정리 및 상태 변경
        affectedProducts.forEach(this::handleProductCategoryDeletion);

        productRepository.saveAll(affectedProducts);

        log.info("Integrated category deletion for {} products", affectedProducts.size());
    }

    @Transactional
    public void handleBrandDeleted(BrandId brandId) {
        log.info("Integrating brand deletion: brandId={}", brandId.getValue());

        List<Product> affectedProducts = productRepository.findByBrandId(brandId);

        if (affectedProducts.isEmpty()) {
            return;
        }

        // 브랜드 참조 정리
        affectedProducts.forEach(product -> product.changeBrand(null));

        productRepository.saveAll(affectedProducts);

        log.info("Integrated brand deletion for {} products", affectedProducts.size());
    }

    private void processProductStatusChanges(List<Product> products, String newStatus) {
        List<StatusChangeRecord> statusChanges = products.stream()
                .filter(product -> !product.getStatus().equals(newStatus))
                .map(product -> {
                    String oldStatus = product.getStatus();
                    product.changeStatus(newStatus);
                    return new StatusChangeRecord(product.getId(), oldStatus, newStatus);
                })
                .toList();

        if (!statusChanges.isEmpty()) {
            productRepository.saveAll(products);

            // TODO: 이벤트 발행
        }
    }

    private String determineProductStatusBasedOnCategory(String categoryStatus) {
        return switch (categoryStatus) {
            case Product.STATUS_INACTIVE, Product.STATUS_DELETED -> Product.STATUS_INACTIVE;
            case Product.STATUS_ACTIVE -> null; // 개별 관리
            default -> null;
        };
    }

    private void handleProductCategoryDeletion(Product product) {
        product.changeCategory(null);

        if (!Product.STATUS_INACTIVE.equals(product.getStatus())) {
            String oldStatus = product.getStatus();
            product.changeStatus(Product.STATUS_INACTIVE);

            // TODO: 이벤트 발행
        }
    }

    private record StatusChangeRecord(
            ProductId productId,
            String oldStatus,
            String newStatus
    ) {}
}