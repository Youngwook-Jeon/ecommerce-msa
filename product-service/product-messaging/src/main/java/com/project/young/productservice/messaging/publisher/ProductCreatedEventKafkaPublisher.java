package com.project.young.productservice.messaging.publisher;

import com.project.young.kafka.producer.service.KafkaProducer;
import com.project.young.kafka.product.avro.model.ProductAvroModel;
import com.project.young.productservice.domain.config.ProductServiceConfigData;
import com.project.young.productservice.domain.event.ProductCreatedEvent;
import com.project.young.productservice.domain.ports.output.message.publisher.ProductMessagePublisher;
import com.project.young.productservice.messaging.mapper.ProductMessagingDataMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

@Slf4j
@Component
public class ProductCreatedEventKafkaPublisher implements ProductMessagePublisher {

    private final ProductMessagingDataMapper productMessagingDataMapper;
    private final KafkaProducer<String, ProductAvroModel> kafkaProducer;
    private final ProductServiceConfigData productServiceConfigData;

    public ProductCreatedEventKafkaPublisher(ProductMessagingDataMapper productMessagingDataMapper,
                                             KafkaProducer<String, ProductAvroModel> kafkaProducer,
                                             ProductServiceConfigData productServiceConfigData) {
        this.productMessagingDataMapper = productMessagingDataMapper;
        this.kafkaProducer = kafkaProducer;
        this.productServiceConfigData = productServiceConfigData;
    }

    @Override
    public void publish(ProductCreatedEvent productCreatedEvent) {
        log.info("Received ProductCreatedEvent of product id: {}",
                productCreatedEvent.getProduct().getId().getValue());
        try {
            ProductAvroModel productAvroModel = productMessagingDataMapper
                    .productCreatedEventToAvroModel(productCreatedEvent);
            kafkaProducer.send(productServiceConfigData.getProductTopicName(),
                    productAvroModel.getProductId(),
                    productAvroModel,
                    getCallback(productServiceConfigData.getProductTopicName(), productAvroModel));
        } catch (Exception e) {
            log.error("Error while sending ProductCreatedEvent of product id: {}, " + " error: {}",
                    productCreatedEvent.getProduct().getId().getValue(), e.getMessage());
        }
    }

    private BiConsumer<SendResult<String, ProductAvroModel>, Throwable>
    getCallback(String topicName, ProductAvroModel message) {

        return (result, ex) -> {
            if (ex == null) {
                RecordMetadata metadata = result.getRecordMetadata();
                log.info("Received new metadata. Topic: {}; Partition {}; Offset {}; Timestamp {}, at time {}",
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                        metadata.timestamp(),
                        System.nanoTime());
            } else {
                log.error("Error while sending message {} to topic {}", message.toString(), topicName, ex);
            }
        };
    }
}
