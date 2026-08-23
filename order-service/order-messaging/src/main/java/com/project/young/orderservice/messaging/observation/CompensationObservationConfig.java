package com.project.young.orderservice.messaging.observation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.port.output.CompensationObservationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the active {@link CompensationObservationStrategy} (file default; ELK later).
 */
@Configuration
@EnableConfigurationProperties(CompensationObservationProperties.class)
public class CompensationObservationConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "order-service.saga-events.compensation.observation",
            name = "strategy",
            havingValue = "file",
            matchIfMissing = true
    )
    public CompensationObservationStrategy fileCompensationObservationStrategy(
            ObjectMapper objectMapper,
            CompensationObservationProperties properties
    ) {
        return new FileCompensationObservationStrategy(objectMapper, properties.file().path());
    }

    @Bean
    @ConditionalOnMissingBean(CompensationObservationStrategy.class)
    public CompensationObservationStrategy noOpCompensationObservationStrategy() {
        return compensation -> {
            // Placeholder when a non-file strategy is selected but not yet registered (e.g. elk).
        };
    }

    @Bean
    public CompensationObservationPort compensationObservationPort(
            CompensationObservationStrategy compensationObservationStrategy
    ) {
        return new CompensationObservationPort() {
            @Override
            public void recordManualCompensation(SagaCompensationView compensation) {
                compensationObservationStrategy.emitManualCompensation(compensation);
            }
        };
    }
}
