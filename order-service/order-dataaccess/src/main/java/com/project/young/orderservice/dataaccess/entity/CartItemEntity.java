package com.project.young.orderservice.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @Column(name = "product_id", nullable = false, columnDefinition = "UUID")
    private UUID productId;

    @Column(name = "product_variant_id", nullable = false, columnDefinition = "UUID")
    private UUID productVariantId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variant_options_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<CartItemOptionLineJson> variantOptionsJson = new ArrayList<>();

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CartItemEntity that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
