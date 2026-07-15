package com.project.young.orderservice.dataaccess.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@Profile("!test")
public class InventoryReservationClientConfig {

  @Bean
  RestClient inventoryReservationRestClient(InventoryReservationClientProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
    requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

    return RestClient.builder()
        .baseUrl(properties.getBaseUrl())
        .requestFactory(requestFactory)
        .build();
  }
}
