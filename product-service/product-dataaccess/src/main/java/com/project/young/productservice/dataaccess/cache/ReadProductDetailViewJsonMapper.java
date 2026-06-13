package com.project.young.productservice.dataaccess.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import org.springframework.stereotype.Component;

@Component
public class ReadProductDetailViewJsonMapper {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    public String toJson(ReadProductDetailView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ReadProductDetailView", e);
        }
    }

    public ReadProductDetailView fromJson(String json) {
        try {
            return objectMapper.readValue(json, ReadProductDetailView.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize ReadProductDetailView", e);
        }
    }
}
