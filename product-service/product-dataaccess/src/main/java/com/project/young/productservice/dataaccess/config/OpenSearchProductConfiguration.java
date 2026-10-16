package com.project.young.productservice.dataaccess.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.port.output.ProductSearchSynchronizationPort;
import com.project.young.productservice.dataaccess.search.ProductSearchSynchronizationReader;
import com.project.young.productservice.dataaccess.search.ProductSearchSynchronizationTargetReader;
import com.project.young.productservice.dataaccess.search.OpenSearchProductSynchronizationAdapter;
import com.project.young.productservice.application.port.output.ProductSearchPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.dataaccess.search.OpenSearchProductIndexAliasAdapter;
import com.project.young.productservice.dataaccess.search.CrossFieldsProductQueryBuilder;
import com.project.young.productservice.dataaccess.search.OpenSearchProductQueryBuilder;
import com.project.young.productservice.dataaccess.search.OpenSearchProductSearchAdapter;
import com.project.young.productservice.dataaccess.search.OpenSearchProductBulkWriter;
import com.project.young.productservice.dataaccess.search.ProductSearchIndexBatchReader;
import com.project.young.productservice.dataaccess.search.ProductSearchInitialLoader;
import com.project.young.productservice.dataaccess.search.ProductSearchCategoryNameReader;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "product-service.search", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({OpenSearchProductProperties.class, ProductSearchIndexMappingProperties.class})
public class OpenSearchProductConfiguration {

    @Bean(destroyMethod = "close")
    OpenSearchTransport productSearchTransport(OpenSearchProductProperties properties, ObjectMapper objectMapper) {
        return ApacheHttpClient5TransportBuilder.builder(HttpHost.create(properties.endpoint()))
                .setMapper(new JacksonJsonpMapper(objectMapper))
                .setHttpClientConfigCallback(c -> c.disableAutomaticRetries())
                .setConnectionConfigCallback(c -> c.setConnectTimeout(Timeout.ofMilliseconds(properties.connectTimeoutMillis())))
                .setRequestConfigCallback(r -> r.setResponseTimeout(Timeout.ofMilliseconds(properties.responseTimeoutMillis())))
                .build();
    }

    @Bean
    ProductSearchSynchronizationPort productSearchSynchronization(ProductSearchSynchronizationReader documents,
                                                                  ProductSearchSynchronizationTargetReader targets,
                                                                  OpenSearchProductBulkWriter writer,
                                                                  ProductSearchIndexAliasPort aliases,
                                                                  OpenSearchProductProperties properties) {
        return new OpenSearchProductSynchronizationAdapter(documents, targets, writer, aliases, properties);
    }

    @Bean
    ProductSearchIndexAliasPort productSearchIndexAliases(OpenSearchClient client, ProductSearchIndexMappingProperties mapping) {
        return new OpenSearchProductIndexAliasAdapter(client, mapping);
    }

    @Bean
    OpenSearchClient productSearchClient(OpenSearchTransport productSearchTransport) {
        return new OpenSearchClient(productSearchTransport);
    }

    @Bean
    @ConditionalOnMissingBean(OpenSearchProductQueryBuilder.class)
    OpenSearchProductQueryBuilder productSearchQueryBuilder() {
        return new CrossFieldsProductQueryBuilder();
    }

    @Bean
    OpenSearchProductBulkWriter productSearchBulkWriter(OpenSearchClient productSearchClient) {
        return new OpenSearchProductBulkWriter(productSearchClient);
    }

    @Bean
    ProductSearchInitialLoader productSearchInitialLoader(ProductSearchIndexBatchReader reader,
                                                          OpenSearchProductBulkWriter writer) {
        return new ProductSearchInitialLoader(reader, writer);
    }

    @Bean
    ProductSearchPort openSearchProductSearchPort(OpenSearchClient productSearchClient,
                                                  OpenSearchProductQueryBuilder productSearchQueryBuilder,
                                                  OpenSearchProductProperties properties, ProductSearchCategoryNameReader categoryNames) {
        return new OpenSearchProductSearchAdapter(productSearchClient, productSearchQueryBuilder, properties, categoryNames);
    }
}
