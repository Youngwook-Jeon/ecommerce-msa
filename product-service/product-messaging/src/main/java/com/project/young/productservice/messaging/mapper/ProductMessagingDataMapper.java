package com.project.young.productservice.messaging.mapper;

import com.project.young.kafka.product.avro.model.ProductAvroModel;
import com.project.young.productservice.domain.event.ProductCreatedEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ProductMessagingDataMapper {

//    public ProductAvroModel productCreatedEventToAvroModel(ProductCreatedEvent productCreatedEvent) {
//        return ProductAvroModel.newBuilder()
//                .setProductId(productCreatedEvent.getProduct().getId().getValue().toString())
//                .setProductName(productCreatedEvent.getProduct().getProductName())
//                .setDescription(productCreatedEvent.getProduct().getDescription())
//                .setPrice(productCreatedEvent.getProduct().getPrice().getAmount())
//                .build();
//    }

    public ProductAvroModel productCreatedEventToAvroModel(ProductCreatedEvent productCreatedEvent) {
        Objects.requireNonNull(productCreatedEvent, "ProductCreatedEvent cannot be null");
        Objects.requireNonNull(productCreatedEvent.getProduct(), "Product cannot be null");
        Objects.requireNonNull(productCreatedEvent.getCreatedAt(), "Timestamp cannot be null");

        return ProductAvroModel.newBuilder()
                .setProductId(Objects.requireNonNull(
                        productCreatedEvent.getProduct().getId().getValue().toString(), "Product ID cannot be null"))
                .setProductName(Objects.requireNonNull(
                        productCreatedEvent.getProduct().getProductName(), "Product name cannot be null"))
                .setDescription(Objects.requireNonNull(
                        productCreatedEvent.getProduct().getDescription(), "Product description cannot be null"))
                .setPrice(Objects.requireNonNull(
                        productCreatedEvent.getProduct().getPrice().getAmount(), "Price cannot be null"))
                .build();
    }
}
