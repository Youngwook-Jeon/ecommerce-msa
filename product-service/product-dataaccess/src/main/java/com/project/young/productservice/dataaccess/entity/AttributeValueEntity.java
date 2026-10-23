package com.project.young.productservice.dataaccess.entity;

import com.project.young.productservice.domain.valueobject.AttributeDataType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "attribute_values")
@Getter
@Setter
@NoArgsConstructor
public class AttributeValueEntity {
    @Id
    private UUID id;

    @Column(name = "attribute_id", nullable = false)
    private UUID attributeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 10)
    private AttributeDataType dataType;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "text_value", length = 100)
    private String textValue;

    @Column(name = "numeric_value", precision = 19, scale = 6)
    private BigDecimal numericValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "date_value")
    private LocalDate dateValue;
}
