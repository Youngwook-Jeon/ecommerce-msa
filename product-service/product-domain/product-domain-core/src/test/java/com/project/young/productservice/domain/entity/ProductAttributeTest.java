package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductAttributeId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.valueobject.ConditionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductAttributeTest {
    @Test
    void changing_a_value_preserves_the_attribute_identity() {
        var attribute = attribute();
        var replacement = new AttributeValueId(UUID.randomUUID());
        var updated = attribute.changeValue(replacement);
        assertThat(updated.getId()).isEqualTo(attribute.getId());
        assertThat(updated.getAttributeId()).isEqualTo(attribute.getAttributeId());
        assertThat(updated.getAttributeValueId()).isEqualTo(replacement);
        assertThat(attribute.getAttributeValueId()).isNotEqualTo(replacement);
    }

    @Test
    void product_specifications_are_immutable_to_callers() {
        var product = product().build();
        product.setAttribute(attribute());
        assertThatThrownBy(() -> product.getAttributes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void duplicate_definitions_and_reassigned_identities_are_rejected() {
        var attribute = attribute();
        assertThatThrownBy(() -> product().attributes(List.of(attribute, attribute)).build())
                .isInstanceOf(ProductDomainException.class);
        var product = product().attributes(List.of(attribute)).build();
        var conflicting = new ProductAttribute(new ProductAttributeId(UUID.randomUUID()), attribute.getAttributeId(),
                new AttributeValueId(UUID.randomUUID()));
        assertThatThrownBy(() -> product.setAttribute(conflicting)).isInstanceOf(ProductDomainException.class);
    }

    @Test
    void deleted_products_cannot_change_specifications() {
        var product = product().build();
        var attribute = attribute();
        product.setAttribute(attribute);
        product.markAsDeleted();
        assertThatThrownBy(() -> product.setAttribute(attribute)).isInstanceOf(ProductDomainException.class);
        assertThatThrownBy(() -> product.removeAttribute(attribute.getAttributeId())).isInstanceOf(ProductDomainException.class);
    }

    @Test
    void missing_identifiers_are_rejected() {
        assertThatThrownBy(() -> new ProductAttribute(null, new AttributeDefinitionId(UUID.randomUUID()),
                new AttributeValueId(UUID.randomUUID()))).isInstanceOf(ProductDomainException.class);
    }

    private ProductAttribute attribute() {
        return new ProductAttribute(new ProductAttributeId(UUID.randomUUID()), new AttributeDefinitionId(UUID.randomUUID()),
                new AttributeValueId(UUID.randomUUID()));
    }

    private Product.Builder product() {
        return Product.builder().productId(new ProductId(UUID.randomUUID())).name("Laptop")
                .description("A laptop for specification model tests").brand("Brand").conditionType(ConditionType.NEW)
                .basePrice(new Money(BigDecimal.TEN)).mainImageUrl("https://example.com/laptop.jpg");
    }
}
