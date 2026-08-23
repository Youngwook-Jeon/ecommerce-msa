package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;

/**
 * Pluggable observation sink for compensation events (local file now, ELK later).
 */
public interface CompensationObservationPort {

    void recordManualCompensation(SagaCompensationView compensation);
}
