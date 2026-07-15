package com.project.young.productservice.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "product-service.inventory")
public class InventoryReservationProperties {

    /**
     * Soft-hold TTL after a successful reserve. Default 15 minutes.
     */
    private Duration reservationTtl = Duration.ofMinutes(15);

    private int expireBatchSize = 100;

    private long expireFixedDelayMs = 30_000L;

    private int maxOptimisticAttempts = 3;

    public Duration getReservationTtl() {
        return reservationTtl;
    }

    public void setReservationTtl(Duration reservationTtl) {
        this.reservationTtl = reservationTtl;
    }

    public int getExpireBatchSize() {
        return expireBatchSize;
    }

    public void setExpireBatchSize(int expireBatchSize) {
        this.expireBatchSize = expireBatchSize;
    }

    public long getExpireFixedDelayMs() {
        return expireFixedDelayMs;
    }

    public void setExpireFixedDelayMs(long expireFixedDelayMs) {
        this.expireFixedDelayMs = expireFixedDelayMs;
    }

    public int getMaxOptimisticAttempts() {
        return maxOptimisticAttempts;
    }

    public void setMaxOptimisticAttempts(int maxOptimisticAttempts) {
        this.maxOptimisticAttempts = maxOptimisticAttempts;
    }
}
