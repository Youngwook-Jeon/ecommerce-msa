package com.project.young.productservice.config;

import com.project.young.productservice.application.exception.ProductSearchUnavailableException;
import com.project.young.productservice.application.port.output.ProductSearchPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicitly disabled search returns 503 while leaving other product features available. */
@Configuration(proxyBeanMethods = false)
public class ProductSearchConfiguration {

    @Bean
    @ConditionalOnMissingBean(ProductSearchPort.class)
    @ConditionalOnProperty(prefix = "product-service.search", name = "enabled", havingValue = "false")
    ProductSearchPort unavailableProductSearchPort() {
        return criteria -> {
            throw new ProductSearchUnavailableException("Product search adapter is not configured");
        };
    }
}
