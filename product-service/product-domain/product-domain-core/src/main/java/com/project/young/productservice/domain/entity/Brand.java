package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.productservice.domain.exception.BrandDomainException;
import lombok.Getter;

import java.time.Instant;

@Getter
public class Brand extends AggregateRoot<BrandId> {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    private String name;
    private String logoUrl;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    private Brand(Builder builder) {
        super.setId(builder.brandId);
        this.name = builder.name;
        this.logoUrl = builder.logoUrl;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    private Brand(BrandId brandId, String name, String logoUrl, String status,
                  Instant createdAt, Instant updatedAt) {
        super.setId(brandId);
        this.name = name;
        this.logoUrl = logoUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void changeName(String name) {
        if (isDeleted()) {
            throw new BrandDomainException("Cannot change the name of a deleted brand.");
        }
        validateBrandName(name);
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void changeLogoUrl(String logoUrl) {
        if (isDeleted()) {
            throw new BrandDomainException("Cannot change the logo URL of a deleted brand.");
        }
        // logoUrl은 null 허용 (선택적 필드)
        this.logoUrl = logoUrl;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(String status) {
        if (isDeleted()) {
            throw new BrandDomainException("Cannot change the status of a deleted brand.");
        }
        if (STATUS_DELETED.equals(status)) {
            throw new BrandDomainException("Cannot change the status as deleted. Use markAsDeleted() instead.");
        }
        if (!isValidStatus(status)) {
            throw new BrandDomainException("Invalid status: " + status);
        }
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void markAsDeleted() {
        if (isDeleted()) {
            return; // Idempotent
        }
        this.status = STATUS_DELETED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(this.status);
    }

    public boolean isDeleted() {
        return STATUS_DELETED.equals(this.status);
    }

    private static boolean isValidStatus(String status) {
        return STATUS_ACTIVE.equals(status) || STATUS_INACTIVE.equals(status);
    }

    private static void validateBrandName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BrandDomainException("Brand name cannot be null or empty.");
        }
        if (name.length() > 100) {
            throw new BrandDomainException("Brand name must be between 1 and 100 characters.");
        }
    }

    /**
     * !!! FOR PERSISTENCE MAPPING ONLY !!!
     * Reconstitutes a Brand object from a persistent state (e.g., database).
     * This method bypasses initial creation validations and should NOT be used
     * for creating new business objects. Use the builder for new instances.
     */
    public static Brand reconstitute(BrandId brandId, String name, String logoUrl, String status,
                                     Instant createdAt, Instant updatedAt) {
        return new Brand(brandId, name, logoUrl, status, createdAt, updatedAt);
    }

    public static final class Builder {
        private BrandId brandId;
        private String name;
        private String logoUrl;
        private String status = STATUS_ACTIVE;
        private Instant createdAt;
        private Instant updatedAt;

        private Builder() {}

        public Builder brandId(BrandId brandId) {
            this.brandId = brandId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder logoUrl(String logoUrl) {
            this.logoUrl = logoUrl;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Brand build() {
            validate();
            setDefaults();
            return new Brand(this);
        }

        private void setDefaults() {
            if (brandId == null) {
                brandId = new BrandId();
            }

            if (status == null) {
                status = STATUS_ACTIVE;
            }

            Instant now = Instant.now();
            if (createdAt == null) {
                createdAt = now;
            }

            if (updatedAt == null) {
                updatedAt = now;
            }
        }


        private void validate() {
            Brand.validateBrandName(name);

            if (status != null && !Brand.isValidStatus(status)) {
                throw new BrandDomainException("Brand must have a valid initial status (ACTIVE, INACTIVE).");
            }
        }
    }
}