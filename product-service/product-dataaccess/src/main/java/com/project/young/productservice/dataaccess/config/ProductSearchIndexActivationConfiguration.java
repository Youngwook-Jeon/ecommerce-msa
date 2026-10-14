package com.project.young.productservice.dataaccess.config;

import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.service.ProductSearchIndexActivationExecutor;
import com.project.young.productservice.dataaccess.search.ProductSearchIndexActivationScheduler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "product-service.search", name = {"enabled", "worker.enabled"}, havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ProductSearchIndexWorkerProperties.class)
public class ProductSearchIndexActivationConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService productSearchActivationThreads() {
        return Executors.newSingleThreadExecutor(r -> new Thread(r, "product-index-activation"));
    }

    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService productSearchActivationHeartbeat() {
        return Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "product-index-activation-heartbeat"));
    }

    @Bean
    ProductSearchIndexActivationExecutor productSearchIndexActivationExecutor(ProductSearchIndexActivationWorkPort store,
                                                                              ProductSearchIndexAliasPort aliases,
                                                                              @Qualifier("productSearchActivationHeartbeat") ScheduledExecutorService heartbeat,
                                                                              ProductSearchIndexWorkerProperties properties) {
        return new ProductSearchIndexActivationExecutor(store, aliases, heartbeat,
                Duration.ofMillis(properties.leaseMillis()), Duration.ofMillis(properties.heartbeatIntervalMillis()));
    }

    @Bean
    ProductSearchIndexActivationScheduler productSearchIndexActivationScheduler(ProductSearchIndexActivationExecutor worker,
                                                                                @Qualifier("productSearchActivationThreads") ExecutorService threads) {
        return new ProductSearchIndexActivationScheduler(worker, threads);
    }
}
