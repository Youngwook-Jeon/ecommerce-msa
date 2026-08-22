package com.project.young.paymentservice.it.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;

/**
 * Kafka/DB ITs use {@code WebEnvironment.NONE} and a fake issuer URI, so Spring cannot
 * build a JwtDecoder from OIDC metadata. Provide a local HS256 decoder for context startup.
 */
@TestConfiguration
public class PaymentIntegrationTestConfiguration {

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec("integration-test-secret-key-32b!!".getBytes(), "HmacSHA256")
        ).build();
    }
}
