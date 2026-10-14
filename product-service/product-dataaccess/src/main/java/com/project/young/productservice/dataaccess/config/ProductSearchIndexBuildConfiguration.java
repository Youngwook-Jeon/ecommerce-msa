package com.project.young.productservice.dataaccess.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildExecutionPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildWorkPort;
import com.project.young.productservice.application.service.ProductSearchIndexBuildExecutor;
import com.project.young.productservice.dataaccess.search.OpenSearchProductIndexBuildExecutionAdapter;
import com.project.young.productservice.dataaccess.search.OpenSearchProductQueryBuilder;
import com.project.young.productservice.dataaccess.search.ProductSearchIndexBuildScheduler;
import com.project.young.productservice.dataaccess.search.ProductSearchIndexVerificationReader;
import com.project.young.productservice.dataaccess.search.ProductSearchInitialLoader;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "product-service.search", name = {"enabled", "worker.enabled"}, havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({ProductSearchIndexWorkerProperties.class, ProductSearchIndexMappingProperties.class})
public class ProductSearchIndexBuildConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService productSearchBuildThreads() {
        return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "product-index-build"));
    }

    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService productSearchBuildHeartbeat() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "product-index-heartbeat"));
    }

    @Bean
    ProductSearchIndexBuildExecutionPort productSearchIndexBuildExecution(OpenSearchClient client, ObjectMapper json,
                                                                          ProductSearchInitialLoader loader, ProductSearchIndexVerificationReader reader,
                                                                          OpenSearchProductQueryBuilder queries, OpenSearchProductProperties properties,
                                                                          ProductSearchIndexMappingProperties mappingProperties) {
        return new OpenSearchProductIndexBuildExecutionAdapter(client, json, loader, reader, queries, properties, mappingProperties);
    }

    @Bean
    ProductSearchIndexBuildExecutor productSearchIndexBuildExecutor(ProductSearchIndexBuildWorkPort store,
                                                                    ProductSearchIndexBuildExecutionPort execution,
                                                                    @Qualifier("productSearchBuildHeartbeat") ScheduledExecutorService productSearchBuildHeartbeat,
                                                                    ProductSearchIndexWorkerProperties properties) {
        return new ProductSearchIndexBuildExecutor(store, execution, productSearchBuildHeartbeat,
                Duration.ofMillis(properties.leaseMillis()), Duration.ofMillis(properties.heartbeatIntervalMillis()));
    }

    @Bean
    ProductSearchIndexBuildScheduler productSearchIndexBuildScheduler(ProductSearchIndexBuildExecutor worker,
                                                                      @Qualifier("productSearchBuildThreads") ExecutorService productSearchBuildThreads) {
        return new ProductSearchIndexBuildScheduler(worker, productSearchBuildThreads);
    }
}
