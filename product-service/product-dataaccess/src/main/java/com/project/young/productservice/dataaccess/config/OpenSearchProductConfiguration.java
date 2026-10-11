package com.project.young.productservice.dataaccess.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.port.output.ProductSearchPort;
import com.project.young.productservice.dataaccess.search.CrossFieldsProductQueryBuilder;
import com.project.young.productservice.dataaccess.search.OpenSearchProductQueryBuilder;
import com.project.young.productservice.dataaccess.search.OpenSearchProductSearchAdapter;
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
@EnableConfigurationProperties(OpenSearchProductProperties.class)
public class OpenSearchProductConfiguration {

    @Bean(destroyMethod = "close")
    OpenSearchTransport productSearchTransport(OpenSearchProductProperties properties, ObjectMapper objectMapper) {
        return ApacheHttpClient5TransportBuilder.builder(HttpHost.create(properties.endpoint()))
                .setMapper(new JacksonJsonpMapper(objectMapper))
                .setConnectionConfigCallback(c -> c.setConnectTimeout(Timeout.ofMilliseconds(properties.connectTimeoutMillis())))
                .setRequestConfigCallback(r -> r.setResponseTimeout(Timeout.ofMilliseconds(properties.responseTimeoutMillis())))
                .build();
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
    ProductSearchPort openSearchProductSearchPort(OpenSearchClient productSearchClient,
                                                OpenSearchProductQueryBuilder productSearchQueryBuilder,
                                                OpenSearchProductProperties properties) {
        return new OpenSearchProductSearchAdapter(productSearchClient, productSearchQueryBuilder, properties);
    }
}
