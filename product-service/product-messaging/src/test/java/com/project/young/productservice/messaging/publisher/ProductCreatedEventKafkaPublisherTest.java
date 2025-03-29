package com.project.young.productservice.messaging.publisher;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.kafka.producer.service.KafkaProducer;
import com.project.young.kafka.product.avro.model.ProductAvroModel;
import com.project.young.productservice.domain.config.ProductServiceConfigData;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.event.ProductCreatedEvent;
import com.project.young.productservice.messaging.mapper.ProductMessagingDataMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCreatedEventKafkaPublisherTest {
    private final String topicName = "product-topic";
    private Product testProduct;
    private static final ProductId productID = new ProductId(UUID.randomUUID());
    private ProductCreatedEvent testEvent;
    private ProductAvroModel avroModel;

    @BeforeEach
    void setup() {
        testProduct = Product.builder()
                .productId(productID)
                .productName("test product")
                .description("test description")
                .price(new Money(BigDecimal.valueOf(100)))
                .build();
        testEvent = new ProductCreatedEvent(testProduct, Instant.now());
        avroModel = ProductAvroModel.newBuilder()
                .setProductId(productID.getValue().toString())
                .setProductName("test product")
                .setDescription("test description")
                .setPrice(BigDecimal.valueOf(100))
                .build();
    }

    @Mock
    private ProductMessagingDataMapper mapper;
    @Mock
    private KafkaProducer<String, ProductAvroModel> kafkaProducer;
    @Mock
    private ProductServiceConfigData configData;

    @InjectMocks
    private ProductCreatedEventKafkaPublisher publisher;

    @Test
    void shouldPublishProductCreatedEvent() {
        // given
        when(configData.getProductTopicName()).thenReturn(topicName);
        when(mapper.productCreatedEventToAvroModel(any(ProductCreatedEvent.class))).thenReturn(avroModel);

        // when
        publisher.publish(testEvent);

        // then
        verify(kafkaProducer).send(
                eq(topicName),
                eq(avroModel.getProductId()),
                eq(avroModel),
                any()
        );
    }

    @Test
    void shouldHandleException_WhenPublishingEventThrowException() {
        // given
        when(mapper.productCreatedEventToAvroModel(testEvent))
                .thenThrow(new RuntimeException("Mapping failed"));

        // when & then - verify no exception is thrown
        assertDoesNotThrow(() -> publisher.publish(testEvent));
    }
}