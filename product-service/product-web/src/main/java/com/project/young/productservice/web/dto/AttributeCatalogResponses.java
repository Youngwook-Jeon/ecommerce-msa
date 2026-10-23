package com.project.young.productservice.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class AttributeCatalogResponses {
    private AttributeCatalogResponses() {
    }

    public record Definition(UUID id, String code, String displayName, String dataType, String canonicalUnit,
                             String status) {
    }

    public record Value(UUID id, UUID attributeId, String dataType, String displayName, String textValue,
                        BigDecimal numericValue, Boolean booleanValue, LocalDate dateValue) {
    }

    public record Policy(UUID attributeId, boolean enabled, boolean required, boolean optionAllowed,
                         boolean filterable, int displayOrder, boolean usable,
                         boolean optionAvailable, boolean filterAvailable) {
    }

    public record CategoryAttribute(Definition attribute, Policy policy) {
    }
}
