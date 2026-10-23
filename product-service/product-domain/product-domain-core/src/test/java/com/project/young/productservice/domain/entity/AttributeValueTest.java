package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeValueTest {
    private final UUID attributeId = UUID.randomUUID();

    @Test
    void normalizesNumericValuesWithoutLosingPrecision() {
        assertThat(value(AttributeDataType.NUMBER, null, new BigDecimal("16.0000000"), null, null).getNumericValue())
                .isEqualByComparingTo("16");
        assertThat(value(AttributeDataType.NUMBER, null, new BigDecimal("0.000001"), null, null).getNumericValue())
                .isEqualByComparingTo("0.000001");
    }

    @Test
    void rejectsMissingMultipleAndMismatchedValues() {
        assertThatThrownBy(() -> value(AttributeDataType.NUMBER, null, null, null, null))
                .isInstanceOf(AttributeDomainException.class);
        assertThatThrownBy(() -> value(AttributeDataType.NUMBER, "16", BigDecimal.TEN, null, null))
                .isInstanceOf(AttributeDomainException.class);
        assertThatThrownBy(() -> value(AttributeDataType.NUMBER, "16", null, null, null))
                .isInstanceOf(AttributeDomainException.class);
    }

    @Test
    void rejectsNumericRoundingAndOverflow() {
        assertThatThrownBy(() -> value(AttributeDataType.NUMBER, null, new BigDecimal("0.0000001"), null, null))
                .isInstanceOf(AttributeDomainException.class);
        assertThatThrownBy(() -> value(AttributeDataType.NUMBER, null, new BigDecimal("10000000000000"), null, null))
                .isInstanceOf(AttributeDomainException.class);
        assertThatThrownBy(() -> value(AttributeDataType.NUMBER, null, new BigDecimal("1E+2147483647"), null, null))
                .isInstanceOf(AttributeDomainException.class);
        assertThatThrownBy(() -> value(AttributeDataType.NUMBER, null,
                new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE), null, null))
                .isInstanceOf(AttributeDomainException.class);
    }

    @Test
    void rejectsMissingIdentityOwnerAndTypeBeforeInspectingValue() {
        var id = new AttributeValueId(UUID.randomUUID());
        var owner = new AttributeDefinitionId(attributeId);
        assertThatThrownBy(() -> new AttributeValue(null, owner, AttributeDataType.NUMBER,
                "Memory", null, BigDecimal.ONE, null, null))
                .isInstanceOf(AttributeDomainException.class).hasMessageContaining("Value ID");
        assertThatThrownBy(() -> new AttributeValue(id, null, AttributeDataType.NUMBER,
                "Memory", null, BigDecimal.ONE, null, null))
                .isInstanceOf(AttributeDomainException.class).hasMessageContaining("Attribute ID");
        assertThatThrownBy(() -> new AttributeValue(id, owner, null, "Memory", null, BigDecimal.ONE, null, null))
                .isInstanceOf(AttributeDomainException.class).hasMessageContaining("data type");
    }

    @Test
    void renameRejectsInvalidLabelsAndPreservesCanonicalValue() {
        var original = value(AttributeDataType.NUMBER, null, new BigDecimal("16.00"), null, null);
        for (String name : new String[]{null, "", " \t\n ", "a".repeat(101)}) {
            assertThatThrownBy(() -> original.rename(name)).isInstanceOf(AttributeDomainException.class);
        }
        var renamed = original.rename(" " + "a".repeat(100) + " ");
        assertThat(renamed.getDisplayName()).isEqualTo("a".repeat(100));
        assertThat(renamed.getId()).isEqualTo(original.getId());
        assertThat(renamed.getAttributeId()).isEqualTo(original.getAttributeId());
        assertThat(renamed.getDataType()).isEqualTo(AttributeDataType.NUMBER);
        assertThat(renamed.getNumericValue()).isEqualByComparingTo("16");
        assertThat(original.getDisplayName()).isEqualTo("Example");
    }

    @Test
    void validatesTextAndDateBoundaries() {
        assertThat(value(AttributeDataType.TEXT, "a".repeat(100), null, null, null).getTextValue()).hasSize(100);
        assertThatThrownBy(() -> value(AttributeDataType.TEXT, "a".repeat(101), null, null, null))
                .isInstanceOf(AttributeDomainException.class);
        assertThat(value(AttributeDataType.DATE, null, null, null, LocalDate.of(1, 1, 1)).getDateValue())
                .isEqualTo(LocalDate.of(1, 1, 1));
        assertThat(value(AttributeDataType.DATE, null, null, null, LocalDate.of(9999, 12, 31)).getDateValue())
                .isEqualTo(LocalDate.of(9999, 12, 31));
        assertThatThrownBy(() -> value(AttributeDataType.DATE, null, null, null, LocalDate.of(0, 12, 31)))
                .isInstanceOf(AttributeDomainException.class);
        assertThatThrownBy(() -> value(AttributeDataType.DATE, null, null, null, LocalDate.of(10000, 1, 1)))
                .isInstanceOf(AttributeDomainException.class);
    }

    @Test
    void handlesFalseDatesAndTrimmedText() {
        assertThat(value(AttributeDataType.BOOLEAN, null, null, false, null).getBooleanValue()).isFalse();
        assertThat(value(AttributeDataType.DATE, null, null, null, LocalDate.of(2026, 3, 1)).getDateValue())
                .isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(value(AttributeDataType.ENUM, " BLACK ", null, null, null).getTextValue()).isEqualTo("BLACK");
        assertThatThrownBy(() -> value(AttributeDataType.TEXT, " ", null, null, null))
                .isInstanceOf(AttributeDomainException.class);
    }

    @Test
    void onlyNumericDefinitionsMaySpecifyUnitsAndCodesAreStableIdentifiers() {
        assertThatThrownBy(() -> new AttributeDefinition(new AttributeDefinitionId(attributeId), "Bad Code", "Name", AttributeDataType.TEXT, null))
                .isInstanceOf(AttributeDomainException.class);
        assertThatThrownBy(() -> new AttributeDefinition(new AttributeDefinitionId(attributeId), "color", "Color", AttributeDataType.ENUM, "GB"))
                .isInstanceOf(AttributeDomainException.class);
    }

    private AttributeValue value(AttributeDataType type, String text, BigDecimal number, Boolean bool, LocalDate date) {
        return new AttributeValue(new AttributeValueId(UUID.randomUUID()), new AttributeDefinitionId(attributeId), type, "Example", text, number, bool, date);
    }
}
