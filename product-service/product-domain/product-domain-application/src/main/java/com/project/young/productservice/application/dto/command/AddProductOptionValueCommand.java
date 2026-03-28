package com.project.young.productservice.application.dto.command;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * boolean {@code is*} 필드는 Bean 규칙상 JSON 키 {@code default} 등으로 잘못 매핑될 수 있어,
 * Jackson은 필드와 {@link JsonProperty} 이름으로만 바인딩한다.
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddProductOptionValueCommand {

    @NotNull(message = "OptionValue id must not be null.")
    private UUID optionValueId;

    @NotNull(message = "Price delta must not be null.")
    @DecimalMin(value = "0.00", inclusive = true, message = "Price delta must be greater than or equal to zero.")
    private BigDecimal priceDelta;

    @JsonProperty("isDefault")
    @Builder.Default
    private boolean isDefault = false;

    @JsonProperty("isActive")
    @Builder.Default
    private boolean isActive = true;
}
