package com.project.young.orderservice.it.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;

import javax.crypto.spec.SecretKeySpec;

@TestConfiguration
public class OrderIntegrationTestConfiguration {

    @Bean
    CatalogTestRestClientHolder catalogTestRestClientHolder() {
        return CatalogTestRestClientHolder.create();
    }

    @Bean
    @Primary
    RestClient productCatalogRestClient(CatalogTestRestClientHolder holder) {
        return holder.restClient();
    }

    @Bean
    @Primary
    RestClient inventoryReservationRestClient() {
        return RestClient.builder().baseUrl("http://inventory.test").build();
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec("integration-test-secret-key-32b!!".getBytes(), "HmacSHA256")
        ).build();
    }
}
