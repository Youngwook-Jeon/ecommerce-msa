package com.project.young.orderservice.dataaccess.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

@Component
public class GuestCartDocumentJsonMapper {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public String toJson(GuestCartDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize guest cart document", e);
        }
    }

    public GuestCartDocument fromJson(String json) {
        try {
            return objectMapper.readValue(json, GuestCartDocument.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize guest cart document", e);
        }
    }
}
