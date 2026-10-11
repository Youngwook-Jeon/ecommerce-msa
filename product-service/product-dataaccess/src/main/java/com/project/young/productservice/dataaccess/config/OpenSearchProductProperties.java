package com.project.young.productservice.dataaccess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;

@ConfigurationProperties("product-service.search")
public record OpenSearchProductProperties(
        @DefaultValue("http://localhost:9200") URI endpoint,
        @DefaultValue("products-read") String index,
        @DefaultValue("1000") int connectTimeoutMillis,
        @DefaultValue("5000") int responseTimeoutMillis,
        @DefaultValue("3") int queryTimeoutSeconds,
        @DefaultValue("10000") int requestBudgetMillis
) {
    public OpenSearchProductProperties {
        if (endpoint == null || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !(endpoint.getScheme().equals("http") || endpoint.getScheme().equals("https"))
                || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/"))) {
            throw new IllegalArgumentException("Search endpoint must be an HTTP(S) origin without credentials");
        }
        if (index == null || index.isBlank() || index.contains(",") || index.contains("*")
                || connectTimeoutMillis <= 0 || responseTimeoutMillis <= 0
                || queryTimeoutSeconds <= 0 || requestBudgetMillis <= 0) {
            throw new IllegalArgumentException("Search index and timeouts must be valid");
        }
    }
}
