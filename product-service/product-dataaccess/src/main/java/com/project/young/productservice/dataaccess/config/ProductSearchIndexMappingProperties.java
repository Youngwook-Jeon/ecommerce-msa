package com.project.young.productservice.dataaccess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

@ConfigurationProperties("product-service.search.mapping")
public record ProductSearchIndexMappingProperties(
        @DefaultValue("classpath:opensearch/products-index.json") Resource resource
) {
    public ProductSearchIndexMappingProperties {
        if (resource == null) {
            throw new IllegalArgumentException("Index mapping resource must be configured");
        }
    }
}
