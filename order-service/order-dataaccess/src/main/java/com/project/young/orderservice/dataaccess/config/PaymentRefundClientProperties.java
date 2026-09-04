package com.project.young.orderservice.dataaccess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order-service.payment-refund")
public record PaymentRefundClientProperties(String baseUrl) {
}
