package com.project.young.productservice.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "product_attributes")
@Getter
@Setter
@NoArgsConstructor
public class ProductAttributeEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private ProductEntity product;

    @Column(name = "attribute_id", nullable = false, updatable = false)
    private UUID attributeId;

    @Column(name = "attribute_value_id", nullable = false)
    private UUID attributeValueId;

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        var attribute = (ProductAttributeEntity) other;
        return id != null && id.equals(attribute.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
