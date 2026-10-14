package com.project.young.productservice.dataaccess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("product-service.search.reconciliation")
public record ProductSearchIndexReconciliationProperties(@DefaultValue("30000") long pollIntervalMillis,
        @DefaultValue("60000") long retryDelayMillis, @DefaultValue("60000") long leaseMillis) {
    public ProductSearchIndexReconciliationProperties {
        if (pollIntervalMillis < 1 || retryDelayMillis < 1 || leaseMillis < 1) {
            throw new IllegalArgumentException("Reconciliation intervals must be positive");
        }
    }
}
