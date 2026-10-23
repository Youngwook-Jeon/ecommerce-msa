package com.project.young.productservice.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "category_attributes")
@Getter
@Setter
@NoArgsConstructor
public class CategoryAttributeEntity {
    @Id
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "attribute_id", nullable = false)
    private UUID attributeId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "option_allowed", nullable = false)
    private boolean optionAllowed;

    @Column(nullable = false)
    private boolean filterable;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
