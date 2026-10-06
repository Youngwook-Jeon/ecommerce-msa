package com.project.young.productservice.web.publicapi.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PublicProductSearchFacetResponse.Terms.class, name = "terms"),
        @JsonSubTypes.Type(value = PublicProductSearchFacetResponse.Range.class, name = "range")
})
public sealed interface PublicProductSearchFacetResponse {

    String key();

    String label();

    record Terms(String key, String label, List<Value> values, boolean hasMore)
            implements PublicProductSearchFacetResponse {
        public Terms {
            values = List.copyOf(values);
        }
    }

    record Value(String value, String label, long count) {
    }

    record Range(String key, String label, BigDecimal min, BigDecimal max)
            implements PublicProductSearchFacetResponse {
    }
}
