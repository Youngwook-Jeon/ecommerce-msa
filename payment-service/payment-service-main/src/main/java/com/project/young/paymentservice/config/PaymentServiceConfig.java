package com.project.young.paymentservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(StubPaymentProperties.class)
public class PaymentServiceConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
