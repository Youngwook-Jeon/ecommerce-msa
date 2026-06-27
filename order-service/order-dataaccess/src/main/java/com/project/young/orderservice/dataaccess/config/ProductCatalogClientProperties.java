package com.project.young.orderservice.dataaccess.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "order-service.product-catalog")
public class ProductCatalogClientProperties {

    private String baseUrl = "http://localhost:9002";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 3000;
}
