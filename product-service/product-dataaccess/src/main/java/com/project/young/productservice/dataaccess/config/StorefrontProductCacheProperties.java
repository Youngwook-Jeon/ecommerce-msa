package com.project.young.productservice.dataaccess.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "product-service.storefront-cache")
public class StorefrontProductCacheProperties {

    private boolean enabled = false;
    private String keyPrefix = "ecomart:product:storefront:";
    private long ttlSeconds = 120;
    private long ttlJitterSeconds = 20;
    private long lockTtlSeconds = 10;
    private int lockWaitRetries = 5;
    private long lockWaitMillis = 50;
}
