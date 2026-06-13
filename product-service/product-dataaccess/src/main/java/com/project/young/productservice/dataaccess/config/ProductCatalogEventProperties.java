package com.project.young.productservice.dataaccess.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "product-service.catalog-events")
public class ProductCatalogEventProperties {

    private String topicName = "product.catalog.changed";
    private CatalogEventRelay relay = CatalogEventRelay.DEBEZIUM;
    private long outboxPollIntervalMs = 2_000;
    private int outboxBatchSize = 50;
}
