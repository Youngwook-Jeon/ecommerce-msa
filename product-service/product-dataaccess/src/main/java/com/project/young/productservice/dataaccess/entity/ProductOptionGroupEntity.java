package com.project.young.productservice.dataaccess.entity;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.*;

@Entity
@Table(
        name = "product_option_groups",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_product_group",
                        columnNames = {"product_id", "option_group_id"}
                ),
                @UniqueConstraint(
                        name = "uk_product_step",
                        columnNames = {"product_id", "step_order"}
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductOptionGroupEntity {

    private static final TimeBasedEpochGenerator UUID_GENERATOR = Generators.timeBasedEpochGenerator();

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "option_group_id", nullable = false, columnDefinition = "UUID")
    private UUID optionGroupId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "is_required", nullable = false)
    private boolean isRequired;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "option_status")
    private OptionStatusEntity status = OptionStatusEntity.ACTIVE;

    @Builder.Default
    @OneToMany(mappedBy = "productOptionGroup", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<ProductOptionValueEntity> optionValues = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void prePersist() {
        if (this.id == null) {
            this.id = UUID_GENERATOR.generate();
        }
    }

    public void addOptionValue(ProductOptionValueEntity optionValue) {
        if (this.optionValues == null) {
            this.optionValues = new HashSet<>();
        }
        this.optionValues.add(optionValue);
        optionValue.setProductOptionGroup(this);
    }

    public boolean isActive() {
        return this.status == OptionStatusEntity.ACTIVE;
    }

    public void setActive(boolean active) {
        this.status = active ? OptionStatusEntity.ACTIVE : OptionStatusEntity.INACTIVE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductOptionGroupEntity that = (ProductOptionGroupEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
