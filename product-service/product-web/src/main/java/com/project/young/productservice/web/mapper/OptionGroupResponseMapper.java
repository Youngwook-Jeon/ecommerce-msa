package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.result.*;
import com.project.young.productservice.web.converter.OptionStatusWebConverter;
import com.project.young.productservice.web.dto.*;
import com.project.young.productservice.web.message.OptionGroupResponseMessageFactory;
import org.springframework.stereotype.Component;

@Component
public class OptionGroupResponseMapper {

    private final OptionGroupResponseMessageFactory messageFactory;
    private final OptionStatusWebConverter optionStatusWebConverter;

    public OptionGroupResponseMapper(OptionGroupResponseMessageFactory messageFactory,
                                     OptionStatusWebConverter optionStatusWebConverter) {
        this.messageFactory = messageFactory;
        this.optionStatusWebConverter = optionStatusWebConverter;
    }

    public CreateOptionGroupResponse toCreateOptionGroupResponse(CreateOptionGroupResult result) {
        return CreateOptionGroupResponse.builder()
                .id(result.id())
                .name(result.name())
                .message(messageFactory.groupCreated())
                .build();
    }

    public AddOptionValueResponse toAddOptionValueResponse(AddOptionValueResult result) {
        return AddOptionValueResponse.builder()
                .id(result.id())
                .value(result.value())
                .message(messageFactory.valueAdded())
                .build();
    }

    public UpdateOptionGroupResponse toUpdateOptionGroupResponse(UpdateOptionGroupResult result) {
        return UpdateOptionGroupResponse.builder()
                .id(result.id())
                .name(result.name())
                .displayName(result.displayName())
                .status(optionStatusWebConverter.toStringValue(result.status()))
                .message(messageFactory.groupUpdated())
                .build();
    }

    public UpdateOptionValueResponse toUpdateOptionValueResponse(UpdateOptionValueResult result) {
        return UpdateOptionValueResponse.builder()
                .id(result.id())
                .value(result.value())
                .displayName(result.displayName())
                .sortOrder(result.sortOrder())
                .status(optionStatusWebConverter.toStringValue(result.status()))
                .message(messageFactory.valueUpdated())
                .build();
    }

    public DeleteOptionGroupResponse toDeleteOptionGroupResponse(DeleteOptionGroupResult result) {
        return DeleteOptionGroupResponse.builder()
                .id(result.id())
                .name(result.name())
                .message(messageFactory.groupDeleted())
                .build();
    }

    public DeleteOptionValueResponse toDeleteOptionValueResponse(DeleteOptionValueResult result) {
        return DeleteOptionValueResponse.builder()
                .id(result.id())
                .value(result.value())
                .message(messageFactory.valueDeleted())
                .build();
    }
}