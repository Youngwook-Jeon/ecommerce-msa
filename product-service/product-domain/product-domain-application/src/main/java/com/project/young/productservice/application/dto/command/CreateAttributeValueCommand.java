package com.project.young.productservice.application.dto.command;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateAttributeValueCommand(String displayName, String textValue, BigDecimal numericValue,
                                         Boolean booleanValue, LocalDate dateValue) {
}
