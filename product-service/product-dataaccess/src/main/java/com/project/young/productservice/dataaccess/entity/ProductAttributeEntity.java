package com.project.young.productservice.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "attribute_id", nullable = false, updatable = false)
    private UUID attributeId;

    @Column(name = "attribute_value_id", nullable = false)
    private UUID attributeValueId;
}
