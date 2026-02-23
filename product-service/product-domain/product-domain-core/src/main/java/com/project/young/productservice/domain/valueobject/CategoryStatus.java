package com.project.young.productservice.domain.valueobject;

import com.project.young.productservice.domain.exception.CategoryDomainException;

public enum CategoryStatus {
    ACTIVE("활성"),
    INACTIVE("비활성"),
    DELETED("삭제됨");

    private final String description;

    CategoryStatus(String description) {
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

    public boolean canTransitionTo(CategoryStatus newStatus) {
        if (newStatus == null) return false;
        if (this == newStatus) return true;

        // 이미 삭제된 카테고리는 상태 변경 불가
        if (this == DELETED) return false;

        return switch (this) {
            case ACTIVE -> newStatus == INACTIVE || newStatus == DELETED;
            case INACTIVE -> newStatus == ACTIVE || newStatus == DELETED;
            default -> false;
        };
    }

    public static CategoryStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            throw new CategoryDomainException("Category status cannot be null or empty");
        }
        try {
            return CategoryStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CategoryDomainException("Invalid category status: " + status);
        }
    }
}