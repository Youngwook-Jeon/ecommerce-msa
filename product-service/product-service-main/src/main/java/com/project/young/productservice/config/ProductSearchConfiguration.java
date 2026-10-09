package com.project.young.productservice.config;

import com.project.young.productservice.application.exception.ProductSearchUnavailableException;
import com.project.young.productservice.application.port.output.ProductSearchPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keep the application bootable until a real search adapter is configured. */
@Configuration(proxyBeanMethods = false)
public class ProductSearchConfiguration {

    @Bean
    @ConditionalOnMissingBean(ProductSearchPort.class)
    ProductSearchPort unavailableProductSearchPort() {
        return criteria -> {
            throw new ProductSearchUnavailableException("Product search adapter is not configured");
        };
    }
}
