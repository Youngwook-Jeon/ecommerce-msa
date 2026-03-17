package com.project.young.productservice.domain.valueobject;

import com.project.young.productservice.domain.exception.OptionDomainException;

public enum OptionStatus {
    ACTIVE("활성"),
    INACTIVE("비활성"),
    DELETED("삭제됨");

    private final String description;

    OptionStatus(String description) {
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

    public boolean canTransitionTo(OptionStatus newStatus) {
        if (newStatus == null) return false;
        if (this == newStatus) return true;

        // 이미 삭제된 옵션의 상태 변경 불가
        if (this == DELETED) return false;

        return switch (this) {
            case ACTIVE -> newStatus == INACTIVE || newStatus == DELETED;
            case INACTIVE -> newStatus == ACTIVE || newStatus == DELETED;
            default -> false;
        };
    }

    public static OptionStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            throw new OptionDomainException("Option status cannot be null or empty");
        }
        try {
            return OptionStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new OptionDomainException("Invalid option status: " + status);
        }
    }
}