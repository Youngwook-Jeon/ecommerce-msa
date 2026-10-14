package com.project.young.productservice.dataaccess.config;

import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexReconciliationPort;
import com.project.young.productservice.application.service.ProductSearchIndexActivationReconciler;
import com.project.young.productservice.dataaccess.search.ProductSearchIndexReconciliationScheduler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProductSearchIndexReconciliationProperties.class)
public class ProductSearchIndexReconciliationConfiguration {
    @Bean
    ProductSearchIndexActivationReconciler productSearchIndexActivationReconciler(ProductSearchIndexReconciliationPort store,
            ProductSearchIndexActivationWorkPort activations, ProductSearchIndexAliasPort aliases,
            ProductSearchIndexReconciliationProperties properties) {
        return new ProductSearchIndexActivationReconciler(store, activations, aliases,
                Duration.ofMillis(properties.leaseMillis()), Duration.ofMillis(properties.retryDelayMillis()));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "product-service.search", name = {"enabled", "worker.enabled", "reconciliation.enabled"},
            havingValue = "true", matchIfMissing = true)
    static class Automatic {
        @Bean(destroyMethod = "shutdownNow")
        ExecutorService productSearchReconciliationThreads() {
            return Executors.newSingleThreadExecutor(r -> new Thread(r, "product-index-reconciliation"));
        }

        @Bean
        ProductSearchIndexReconciliationScheduler productSearchIndexReconciliationScheduler(ProductSearchIndexActivationReconciler reconciler,
                @Qualifier("productSearchReconciliationThreads") ExecutorService threads) {
            return new ProductSearchIndexReconciliationScheduler(reconciler, threads);
        }
    }
}
