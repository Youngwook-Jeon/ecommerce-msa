package com.project.young.productservice.dataaccess.entity;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "variant_option_values",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_variant_pov",
                        columnNames = {"variant_id", "product_option_value_id"}
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VariantOptionValueEntity {

    private static final TimeBasedEpochGenerator UUID_GENERATOR = Generators.timeBasedEpochGenerator();

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariantEntity variant;

    @Column(name = "product_option_value_id", nullable = false, columnDefinition = "UUID")
    private UUID productOptionValueId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 생성 시점에 Null을 강제 검증하는 커스텀 빌더
    @Builder
    public VariantOptionValueEntity(UUID id, ProductVariantEntity variant, UUID productOptionValueId) {
        Objects.requireNonNull(productOptionValueId, "productOptionValueId must not be null at creation time.");
        this.id = id;
        this.variant = variant;
        this.productOptionValueId = productOptionValueId;
    }

    @PrePersist
    private void prePersist() {
        if (this.id == null) {
            this.id = UUID_GENERATOR.generate();
        }
    }

    // 비즈니스 키(productOptionValueId) 기반의 동등성 비교
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariantOptionValueEntity that)) return false;
        return Objects.equals(productOptionValueId, that.productOptionValueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productOptionValueId);
    }
}