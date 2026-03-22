package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.port.output.view.ReadOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadOptionValueView;
import com.project.young.productservice.web.converter.OptionStatusWebConverter;
import com.project.young.productservice.web.dto.ReadOptionGroupListQueryResponse;
import com.project.young.productservice.web.dto.ReadOptionGroupQueryResponse;
import com.project.young.productservice.web.dto.ReadOptionValueQueryResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class OptionGroupQueryResponseMapper {

    private final OptionStatusWebConverter optionStatusWebConverter;

    public OptionGroupQueryResponseMapper(OptionStatusWebConverter optionStatusWebConverter) {
        this.optionStatusWebConverter = optionStatusWebConverter;
    }

    public ReadOptionGroupQueryResponse toReadOptionGroupQueryResponse(ReadOptionGroupView view) {
        Objects.requireNonNull(view, "ReadOptionGroupView is null");

        List<ReadOptionValueQueryResponse> values = view.optionValues().stream()
                .map(this::toReadOptionValueQueryResponse)
                .toList();

        return ReadOptionGroupQueryResponse.builder()
                .id(view.id())
                .name(view.name())
                .displayName(view.displayName())
                .status(optionStatusWebConverter.toStringValue(view.status()))
                .optionValues(values)
                .build();
    }

    public ReadOptionGroupListQueryResponse toReadOptionGroupListQueryResponse(List<ReadOptionGroupView> views) {
        List<ReadOptionGroupQueryResponse> groups = views.stream()
                .map(this::toReadOptionGroupQueryResponse)
                .toList();

        return ReadOptionGroupListQueryResponse.builder()
                .optionGroups(groups)
                .build();
    }

    private ReadOptionValueQueryResponse toReadOptionValueQueryResponse(ReadOptionValueView view) {
        Objects.requireNonNull(view, "ReadOptionValueView is null");
        return ReadOptionValueQueryResponse.builder()
                .id(view.id())
                .value(view.value())
                .displayName(view.displayName())
                .sortOrder(view.sortOrder())
                .status(optionStatusWebConverter.toStringValue(view.status()))
                .build();
    }
}
