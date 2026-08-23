package com.project.young.orderservice.messaging.observation;

import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;

/**
 * Pluggable sink for compensation telemetry (local file now; ELK later).
 */
public interface CompensationObservationStrategy {

    void emitManualCompensation(SagaCompensationView compensation);
}
