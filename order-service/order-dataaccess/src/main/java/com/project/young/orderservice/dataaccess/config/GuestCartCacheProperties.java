package com.project.young.orderservice.dataaccess.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "order-service.guest-cart")
public class GuestCartCacheProperties {

    private boolean enabled = false;
    private String keyPrefix = "ecomart:order:cart:guest:";
    private long ttlSeconds = 2_592_000L;
}
