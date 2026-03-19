package com.project.young.productservice.application.mapper;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.application.dto.*;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class OptionGroupDataMapper {

    public CreateOptionGroupResult toCreateOptionGroupResult(OptionGroup optionGroup) {
        Objects.requireNonNull(optionGroup, "OptionGroup cannot be null");
        Objects.requireNonNull(optionGroup.getId(), "OptionGroup ID cannot be null");

        return CreateOptionGroupResult.builder()
                .id(optionGroup.getId().getValue())
                .name(optionGroup.getName())
                .build();
    }

    public OptionGroup toOptionGroup(CreateOptionGroupCommand command, OptionGroupId optionGroupId) {
        Objects.requireNonNull(command, "CreateOptionGroupCommand cannot be null");

        return OptionGroup.builder()
                .id(optionGroupId)
                .name(command.getName())
                .displayName(command.getDisplayName())
                .build();
    }

    public UpdateOptionGroupResult toUpdateOptionGroupResult(OptionGroup optionGroup) {
        Objects.requireNonNull(optionGroup, "OptionGroup cannot be null");

        return UpdateOptionGroupResult.builder()
                .id(optionGroup.getId().getValue())
                .name(optionGroup.getName())
                .displayName(optionGroup.getDisplayName())
                .status(optionGroup.getStatus())
                .build();
    }

    public DeleteOptionGroupResult toDeleteOptionGroupResult(OptionGroup optionGroup) {
        Objects.requireNonNull(optionGroup, "OptionGroup cannot be null");

        return DeleteOptionGroupResult.builder()
                .id(optionGroup.getId().getValue())
                .name(optionGroup.getName())
                .build();
    }

    public OptionValue toOptionValue(AddOptionValueCommand command, OptionValueId optionValueId) {
        Objects.requireNonNull(command, "AddOptionValueCommand cannot be null");

        return OptionValue.builder()
                .id(optionValueId)
                .value(command.getValue())
                .displayName(command.getDisplayName())
                .sortOrder(command.getSortOrder())
                .build();
    }

    public AddOptionValueResult toAddOptionValueResult(OptionValue optionValue) {
        Objects.requireNonNull(optionValue, "OptionValue cannot be null");
        Objects.requireNonNull(optionValue.getId(), "OptionValue ID cannot be null");

        return AddOptionValueResult.builder()
                .id(optionValue.getId().getValue())
                .value(optionValue.getValue())
                .build();
    }

    public UpdateOptionValueResult toUpdateOptionValueResult(OptionValue optionValue) {
        Objects.requireNonNull(optionValue, "OptionValue cannot be null");
        Objects.requireNonNull(optionValue.getId(), "OptionValue ID cannot be null");

        return UpdateOptionValueResult.builder()
                .id(optionValue.getId().getValue())
                .value(optionValue.getValue())
                .displayName(optionValue.getDisplayName())
                .sortOrder(optionValue.getSortOrder())
                .status(optionValue.getStatus())
                .build();
    }

    public DeleteOptionValueResult toDeleteOptionValueResult(OptionValue optionValue) {
        Objects.requireNonNull(optionValue, "OptionValue cannot be null");
        return DeleteOptionValueResult.builder()
                .id(optionValue.getId().getValue())
                .value(optionValue.getValue())
                .build();
    }
}