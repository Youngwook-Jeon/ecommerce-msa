package com.project.young.orderservice.messaging.observation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appends one JSON line per MANUAL compensation to a local file (swap to ELK later).
 */
public final class FileCompensationObservationStrategy implements CompensationObservationStrategy {

    private static final Logger log = LoggerFactory.getLogger(FileCompensationObservationStrategy.class);

    private final ObjectMapper objectMapper;
    private final Path outputPath;

    public FileCompensationObservationStrategy(ObjectMapper objectMapper, String outputPath) {
        this.objectMapper = objectMapper;
        this.outputPath = Path.of(outputPath).toAbsolutePath().normalize();
    }

    @Override
    public void emitManualCompensation(SagaCompensationView compensation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "saga_compensation_manual");
        payload.put("observedAt", Instant.now().toString());
        payload.put("id", compensation.id());
        payload.put("eventId", compensation.eventId());
        payload.put("paymentId", compensation.paymentId());
        payload.put("orderId", compensation.orderId());
        payload.put("userId", compensation.userId());
        payload.put("amount", compensation.amount());
        payload.put("currency", compensation.currency());
        payload.put("sourceTopic", compensation.sourceTopic());
        payload.put("dltTopic", compensation.dltTopic());
        payload.put("sourcePartition", compensation.sourcePartition());
        payload.put("sourceOffset", compensation.sourceOffset());
        payload.put("failureExceptionClass", compensation.failureExceptionClass());
        payload.put("failureMessage", compensation.failureMessage());
        payload.put("recommendedAction", compensation.recommendedAction().name());
        payload.put("refundSla", compensation.refundSla().name());
        payload.put("classificationReason", compensation.classificationReason());
        payload.put("handlingStatus", compensation.handlingStatus().name());
        payload.put("newlyCreated", compensation.newlyCreated());
        payload.put("createdAt", compensation.createdAt() == null ? null : compensation.createdAt().toString());

        try {
            Files.createDirectories(outputPath.getParent());
            String line = objectMapper.writeValueAsString(payload) + System.lineSeparator();
            Files.writeString(
                    outputPath,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ex) {
            // Observation must not fail DLT MANUAL persistence / Kafka ack.
            log.warn(
                    "Failed to append saga compensation observation to {}: {}",
                    outputPath,
                    ex.toString()
            );
        }
    }
}
