package com.project.young.orderservice.messaging.observation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Observation sink settings. Strategy bean is selected by {@code strategy}.
 */
@ConfigurationProperties(prefix = "order-service.saga-events.compensation.observation")
public record CompensationObservationProperties(
        String strategy,
        File file
) {

    public CompensationObservationProperties {
        if (strategy == null || strategy.isBlank()) {
            strategy = "file";
        }
        if (file == null) {
            file = new File("./logs/saga-compensation.jsonl");
        }
    }

    public record File(String path) {
        public File {
            if (path == null || path.isBlank()) {
                path = "./logs/saga-compensation.jsonl";
            }
        }
    }
}
