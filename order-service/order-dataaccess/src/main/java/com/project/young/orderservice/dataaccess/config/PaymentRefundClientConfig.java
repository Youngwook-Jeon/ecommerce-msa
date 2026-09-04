package com.project.young.orderservice.dataaccess.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PaymentRefundClientProperties.class)
public class PaymentRefundClientConfig {
    @Bean
    RestClient paymentRefundRestClient(PaymentRefundClientProperties properties) {
        return RestClient.builder().baseUrl(properties.baseUrl()).build();
    }
}
