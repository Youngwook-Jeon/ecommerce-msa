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

@Component
public class OptionGroupDataAccessMapper {

    public OptionGroup entityToDomain(OptionGroupEntity entity) {
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

    public OptionGroupEntity domainToEntity(OptionGroup domain) {
        Objects.requireNonNull(domain, "domain must not be null.");
        Objects.requireNonNull(domain.getId(), "domain ID must not be null.");

        OptionGroupEntity entity = OptionGroupEntity.builder()
                .id(domain.getId().getValue())
                .name(domain.getName())
                .displayName(domain.getDisplayName())
                .status(toEntityStatus(domain.getStatus()))
                .build();

        if (domain.getOptionValues() != null) {
            domain.getOptionValues().forEach(val -> {
                OptionValueEntity valEntity = optionValueDomainToEntity(val);
                entity.addOptionValue(valEntity);
            });
        }

        return entity;
    }

    public OptionValueEntity optionValueDomainToEntity(OptionValue domain) {
        Objects.requireNonNull(domain, "optionValue domain must not be null.");
        Objects.requireNonNull(domain.getId(), "optionValue domain ID must not be null.");

        return OptionValueEntity.builder()
                .id(domain.getId().getValue())
                .value(domain.getValue())
                .displayName(domain.getDisplayName())
                .sortOrder(domain.getSortOrder())
                .status(toEntityStatus(domain.getStatus()))
                .build();
    }

    public OptionStatusEntity toEntityStatus(OptionStatus domainStatus) {
        Objects.requireNonNull(domainStatus, "domainStatus must not be null.");
        return OptionStatusEntity.valueOf(domainStatus.name());
    }

    public OptionStatus toDomainStatus(OptionStatusEntity entityStatus) {
        Objects.requireNonNull(entityStatus, "entityStatus must not be null.");
        return OptionStatus.valueOf(entityStatus.name());
    }
}