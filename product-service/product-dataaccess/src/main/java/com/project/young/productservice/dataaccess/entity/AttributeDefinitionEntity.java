package com.project.young.productservice.dataaccess.entity;

import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.AttributeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "attribute_definitions")
@Getter
@Setter
@NoArgsConstructor
public class AttributeDefinitionEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 10)
    private AttributeDataType dataType;

    @Column(name = "canonical_unit", length = 20)
    private String canonicalUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttributeStatus status;
}
