package com.project.young.productservice.dataaccess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("product-service.search.worker")
public record ProductSearchIndexWorkerProperties(@DefaultValue("2000") long pollIntervalMillis, @DefaultValue("60000") long leaseMillis,
                                                @DefaultValue("10000") long heartbeatIntervalMillis) {
    public ProductSearchIndexWorkerProperties {
        if (pollIntervalMillis < 1 || heartbeatIntervalMillis < 1 || leaseMillis < 1 || heartbeatIntervalMillis > (leaseMillis - 1) / 2) {
            throw new IllegalArgumentException("Worker intervals must be positive and lease must exceed two heartbeat intervals");
        }
    }
}
