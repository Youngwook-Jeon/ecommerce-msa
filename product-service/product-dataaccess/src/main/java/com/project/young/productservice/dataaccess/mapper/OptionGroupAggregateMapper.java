package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Maps a fully loaded {@link OptionGroupEntity} (e.g. {@code findById} with entity graph including option values)
 * to the domain model. Do not use on partially initialized associations — that risks N+1 selects.
 */
@Component
public class OptionGroupAggregateMapper {

    public OptionGroup toOptionGroup(OptionGroupEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null.");
        Objects.requireNonNull(entity.getId(), "entity ID must not be null.");

        List<OptionValue> optionValues = entity.getOptionValues().stream()
                .map(this::optionValueEntityToDomain)
                .collect(Collectors.toList());

        return OptionGroup.reconstitute(
                new OptionGroupId(entity.getId()),
                entity.getName(),
                entity.getDisplayName(),
                toDomainStatus(entity.getStatus()),
                optionValues
        );
    }

    private OptionValue optionValueEntityToDomain(OptionValueEntity entity) {
        Objects.requireNonNull(entity, "optionValueEntity must not be null.");
        Objects.requireNonNull(entity.getId(), "optionValueEntity ID must not be null.");

        return OptionValue.reconstitute(
                new OptionValueId(entity.getId()),
                entity.getValue(),
                entity.getDisplayName(),
                entity.getSortOrder(),
                toDomainStatus(entity.getStatus())
        );
    }

    private OptionStatus toDomainStatus(OptionStatusEntity entityStatus) {
        return OptionStatus.valueOf(entityStatus.name());
    }
}
