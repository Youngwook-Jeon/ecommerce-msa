package com.project.young.productservice.config;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasState;
import com.project.young.productservice.application.exception.ProductSearchUnavailableException;
import com.project.young.productservice.application.port.output.ProductSearchPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort.Work;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import com.project.young.productservice.application.port.output.ProductSearchSynchronizationPort;
import com.project.young.productservice.application.exception.ProductSearchSynchronizationDeferredException;
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

    @Bean
    @ConditionalOnMissingBean(ProductSearchIndexAliasPort.class)
    @ConditionalOnProperty(prefix = "product-service.search", name = "enabled", havingValue = "false")
    ProductSearchIndexAliasPort unavailableProductSearchIndexAliases() {
        return new ProductSearchIndexAliasPort() {
            private ProductSearchIndexManagementException unavailable() {
                return new ProductSearchIndexManagementException(ProductSearchIndexManagementException.Code.INDEX_MANAGEMENT_UNAVAILABLE,
                        "Product search adapter is not configured");
            }
            public ProductSearchIndexAliasState readAlias(String aliasName) { throw unavailable(); }
            public void verifyBeforeSwitch(Work work) { throw unavailable(); }
            public void switchAlias(Work work) { throw unavailable(); }
            public void verifyAfterSwitch(Work work) { throw unavailable(); }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ProductSearchSynchronizationPort.class)
    @ConditionalOnProperty(prefix = "product-service.search", name = "enabled", havingValue = "false")
    ProductSearchSynchronizationPort unavailableProductSearchSynchronization() {
        return (id, revision) -> { throw new ProductSearchSynchronizationDeferredException("Search is disabled"); };
    }
}
