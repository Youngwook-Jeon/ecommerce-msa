package com.project.young.productservice.domain.valueobject;

import com.project.young.productservice.domain.exception.ProductDomainException;

public enum ProductStatus {
    ACTIVE("활성"),
    INACTIVE("비활성"),
    DISCONTINUED("단종"),
    OUT_OF_STOCK("품절"),
    DELETED("삭제됨");

    private final String description;

    ProductStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isDeleted() {
        return this == DELETED;
    }

    public boolean isOutOfStock() {
        return this == OUT_OF_STOCK;
    }

    public boolean isDiscontinued() {
        return this == DISCONTINUED;
    }

    public boolean canTransitionTo(ProductStatus newStatus) {
        if (newStatus == null) return false;
        if (this == newStatus) return true;

        // 삭제된 제품은 상태 변경 불가
        if (this == DELETED) return false;

        return true;
    }

    public static ProductStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            throw new ProductDomainException("Product status cannot be null or empty");
        }
        try {
            return ProductStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ProductDomainException("Invalid product status: " + status);
        }
    }

}
