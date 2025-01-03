package com.project.young.productservice.messaging.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductID;
import com.project.young.kafka.product.avro.model.ProductAvroModel;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.event.ProductCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ProductMessagingDataMapperTest {

    private ProductMessagingDataMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ProductMessagingDataMapper();
    }

    @Test
    void shouldMapProductCreatedEventToAvroModelCorrectly() {
        // given
        ProductID productId = new ProductID(UUID.randomUUID());
        Product product = Product.builder()
                .productID(productId)
                .productName("Test Product")
                .description("Test Description")
                .price(new Money(BigDecimal.valueOf(100.0)))
                .build();
        ProductCreatedEvent event = new ProductCreatedEvent(product, Instant.now());

        // when
        ProductAvroModel avroModel = mapper.productCreatedEventToAvroModel(event);

        // then
        assertThat(avroModel).isNotNull();
        assertThat(avroModel.getProductId()).isEqualTo(productId.getValue().toString());
        assertThat(avroModel.getProductName()).isEqualTo("Test Product");
        assertThat(avroModel.getDescription()).isEqualTo("Test Description");
        assertThat(avroModel.getPrice()).isEqualTo(BigDecimal.valueOf(100.0));
    }

    @Test
    void shouldThrowException_WhenEventIsNull() {
        // given & when & then
        assertThrows(NullPointerException.class, () -> mapper.productCreatedEventToAvroModel(null));
    }

    @Test
    void shouldThrowException_WhenProductIsNull() {
        // given
        ProductCreatedEvent event = new ProductCreatedEvent(null, Instant.now());

        // when & then
        assertThrows(NullPointerException.class, () -> mapper.productCreatedEventToAvroModel(event));
    }

    @Test
    void shouldThrowException_WhenProductNameIsNull() {
        // given
        ProductID productId = new ProductID(UUID.randomUUID());
        Product product = Product.builder()
                .productID(productId)
                .productName(null)
                .description("Test Description")
                .price(new Money(BigDecimal.valueOf(100.0)))
                .build();
        ProductCreatedEvent event = new ProductCreatedEvent(product, Instant.now());

        // When & Then
        assertThrows(NullPointerException.class, () -> mapper.productCreatedEventToAvroModel(event));
    }
}