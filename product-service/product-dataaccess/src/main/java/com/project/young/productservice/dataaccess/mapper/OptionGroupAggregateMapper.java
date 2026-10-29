package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Converts explicitly loaded parent and value data without triggering lazy association queries. */
@Component
public class OptionGroupAggregateMapper {

    public OptionGroup toOptionGroup(OptionGroupEntity entity, List<OptionValueEntity> values) {
        Objects.requireNonNull(entity, "entity must not be null.");

        Objects.requireNonNull(values, "option values must not be null.");
        if (!Hibernate.isInitialized(entity) || !Hibernate.isInitialized(values)) {
            throw new IllegalStateException("Option group and values must be explicitly loaded before mapping.");
        }
        Objects.requireNonNull(entity.getId(), "entity ID must not be null.");
        List<OptionValue> optionValues = values.stream()
                .map(this::optionValueEntityToDomain)
                .collect(Collectors.toList());

        return OptionGroup.reconstitute(
                new OptionGroupId(entity.getId()),
                entity.getName(),
                entity.getDisplayName(),
                toDomainStatus(entity.getStatus()),
                optionValues,
                entity.getAttributeId() == null ? null : new AttributeDefinitionId(entity.getAttributeId())
        );
    }

    private OptionValue optionValueEntityToDomain(OptionValueEntity entity) {
        Objects.requireNonNull(entity, "optionValueEntity must not be null.");
        if (!Hibernate.isInitialized(entity)) {
            throw new IllegalStateException("Option values must be explicitly loaded before mapping.");
        }
        Objects.requireNonNull(entity.getId(), "optionValueEntity ID must not be null.");

        return OptionValue.reconstitute(
                new OptionValueId(entity.getId()),
                entity.getValue(),
                entity.getDisplayName(),
                entity.getSortOrder(),
                toDomainStatus(entity.getStatus()),
                entity.getAttributeValueId() == null ? null : new AttributeValueId(entity.getAttributeValueId())
        );
    }

    private OptionStatus toDomainStatus(OptionStatusEntity entityStatus) {
        return OptionStatus.valueOf(entityStatus.name());
    }
}
