package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.exception.AttributeInactiveException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.AttributeStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeDefinitionTest {
    private final AttributeDefinitionId id = new AttributeDefinitionId(UUID.randomUUID());

    @Test
    void rejectsEachMissingRequiredFieldWithDomainError() {
        assertThatThrownBy(() -> new AttributeDefinition(null, "memory", "Memory", AttributeDataType.NUMBER, "GB"))
                .isInstanceOf(AttributeDomainException.class).hasMessageContaining("Attribute ID");
        assertThatThrownBy(() -> new AttributeDefinition(id, "memory", "Memory", null, null))
                .isInstanceOf(AttributeDomainException.class).hasMessageContaining("data type");
        assertThatThrownBy(() -> new AttributeDefinition(id, "memory", "Memory", AttributeDataType.NUMBER, "GB", null))
                .isInstanceOf(AttributeDomainException.class).hasMessageContaining("status");
    }

    @Test
    void enforcesStableCodeSyntaxAndLengthBoundaries() {
        for (String code : new String[]{null, "", " Memory ", "1memory", "memory-size", "a".repeat(101)}) {
            assertThatThrownBy(() -> new AttributeDefinition(id, code, "Memory", AttributeDataType.NUMBER, "GB"))
                    .isInstanceOf(AttributeDomainException.class);
        }
        assertThat(new AttributeDefinition(id, "a".repeat(100), "Memory", AttributeDataType.NUMBER, "GB").getCode())
                .hasSize(100);
    }

    @Test
    void validatesUnitsAndNormalizesNumericUnits() {
        for (String unit : new String[]{"", " \t ", "a".repeat(21)}) {
            assertThatThrownBy(() -> new AttributeDefinition(id, "memory", "Memory", AttributeDataType.NUMBER, unit))
                    .isInstanceOf(AttributeDomainException.class);
        }
        for (var type : new AttributeDataType[]{AttributeDataType.TEXT, AttributeDataType.ENUM,
                AttributeDataType.BOOLEAN, AttributeDataType.DATE}) {
            assertThatThrownBy(() -> new AttributeDefinition(id, "example", "Example", type, "GB"))
                    .isInstanceOf(AttributeDomainException.class);
            assertThat(new AttributeDefinition(id, "example", "Example", type, null).getCanonicalUnit()).isNull();
        }
        assertThat(new AttributeDefinition(id, "memory", "Memory", AttributeDataType.NUMBER, " GB ").getCanonicalUnit())
                .isEqualTo("GB");
        assertThat(new AttributeDefinition(id, "memory", "Memory", AttributeDataType.NUMBER, "a".repeat(20)).getCanonicalUnit())
                .hasSize(20);
    }

    @Test
    void constructorAndRenameRejectInvalidLabelsWithoutChangingExistingState() {
        var original = new AttributeDefinition(id, "memory", "Memory", AttributeDataType.NUMBER, "GB").deactivate();
        for (String name : new String[]{null, "", " \t\n ", "a".repeat(101)}) {
            assertThatThrownBy(() -> new AttributeDefinition(id, "memory", name, AttributeDataType.NUMBER, "GB"))
                    .isInstanceOf(AttributeDomainException.class);
            assertThatThrownBy(() -> original.rename(name)).isInstanceOf(AttributeDomainException.class);
        }
        assertThat(original.getDisplayName()).isEqualTo("Memory");
        assertThat(original.getStatus()).isEqualTo(AttributeStatus.INACTIVE);
    }

    @Test
    void renameNormalizesLabelAndPreservesIdentitySemanticsAndStatus() {
        var original = new AttributeDefinition(id, "memory", "Memory", AttributeDataType.NUMBER, "GB").deactivate();
        var renamed = original.rename(" " + "a".repeat(100) + " ");
        assertThat(renamed.getDisplayName()).isEqualTo("a".repeat(100));
        assertThat(renamed.getId()).isEqualTo(id);
        assertThat(renamed.getCode()).isEqualTo("memory");
        assertThat(renamed.getDataType()).isEqualTo(AttributeDataType.NUMBER);
        assertThat(renamed.getCanonicalUnit()).isEqualTo("GB");
        assertThat(renamed.getStatus()).isEqualTo(AttributeStatus.INACTIVE);
        assertThat(original.getDisplayName()).isEqualTo("Memory");
    }

    @Test
    void activeGuardRejectsInactiveDefinitionsAndDeactivationIsIdempotent() {
        var active = new AttributeDefinition(id, "memory", "Memory", AttributeDataType.NUMBER, "GB");
        assertThatCode(active::requireActive).doesNotThrowAnyException();
        var inactive = active.deactivate();
        assertThatThrownBy(inactive::requireActive).isInstanceOf(AttributeInactiveException.class);
        assertThat(inactive.deactivate().getStatus()).isEqualTo(AttributeStatus.INACTIVE);
        assertThat(active.getStatus()).isEqualTo(AttributeStatus.ACTIVE);
    }
}
