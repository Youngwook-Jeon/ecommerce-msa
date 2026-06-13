package com.project.young.productservice.messaging.mapper;

import com.project.young.kafka.product.avro.model.ProductCatalogChangedAvroModel;
import com.project.young.productservice.dataaccess.entity.ProductCatalogOutboxEntity;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalogChangedAvroMapper {

    public ProductCatalogChangedAvroModel toAvro(ProductCatalogOutboxEntity entity) {
        return ProductCatalogChangedAvroModel.newBuilder()
                .setEventId(entity.getEventId().toString())
                .setProductId(entity.getProductId().toString())
                .setCategoryId(entity.getCategoryId())
                .setChangeType(entity.getChangeType())
                .setOccurredAt(entity.getOccurredAt().toEpochMilli())
                .build();
    }
}
