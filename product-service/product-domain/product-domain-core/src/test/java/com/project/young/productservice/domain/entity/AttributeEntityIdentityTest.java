package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.entity.BaseEntity;
import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.AttributeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeEntityIdentityTest {
    @Test
    void definitionUsesAggregateIdentityAcrossPresentationAndStatusChanges() {
        var id = new AttributeDefinitionId(UUID.randomUUID());
        var original = new AttributeDefinition(id, "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB");
        var renamed = original.rename("RAM");
        var inactive = renamed.deactivate();

        assertThat(original).isInstanceOf(AggregateRoot.class);
        assertThat(renamed).isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(inactive).isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(new HashSet<>(List.of(original, renamed, inactive))).hasSize(1);
        assertThat(inactive.getId()).isEqualTo(id);
        assertThat(inactive.getDisplayName()).isEqualTo("RAM");
        assertThat(inactive.getStatus()).isEqualTo(AttributeStatus.INACTIVE);
        assertThat(original.getStatus()).isEqualTo(AttributeStatus.ACTIVE);
    }

    @Test
    void valueUsesEntityIdentityRatherThanItsLabelOrCanonicalContent() {
        var attributeId = new AttributeDefinitionId(UUID.randomUUID());
        var valueId = new AttributeValueId(UUID.randomUUID());
        var original = value(valueId, attributeId);
        var renamed = original.rename("Sixteen gigabytes");

        assertThat(original).isInstanceOf(BaseEntity.class);
        assertThat(original).isNotInstanceOf(AggregateRoot.class);
        assertThat(renamed).isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(renamed.getAttributeId()).isEqualTo(attributeId);
        assertThat(renamed.getDisplayName()).isEqualTo("Sixteen gigabytes");
        assertThat(renamed.getNumericValue()).isEqualByComparingTo("16");
        assertThat(value(new AttributeValueId(UUID.randomUUID()), attributeId)).isNotEqualTo(original);
    }

    @Test
    void typedIdsDistinguishDefinitionsFromValuesEvenForTheSameUuid() {
        UUID rawId = UUID.randomUUID();
        var definitionId = new AttributeDefinitionId(rawId);
        var valueId = new AttributeValueId(rawId);
        assertThat(definitionId).isEqualTo(new AttributeDefinitionId(rawId));
        assertThat(definitionId).isNotEqualTo(valueId);
        var first = new AttributeDefinition(definitionId, "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB");
        var second = new AttributeDefinition(new AttributeDefinitionId(UUID.randomUUID()),
                "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB");
        assertThat(first).isNotEqualTo(second);
    }

    private AttributeValue value(AttributeValueId id, AttributeDefinitionId attributeId) {
        return new AttributeValue(id, attributeId, AttributeDataType.NUMBER, "16 GB",
                null, BigDecimal.valueOf(16), null, null);
    }
}
