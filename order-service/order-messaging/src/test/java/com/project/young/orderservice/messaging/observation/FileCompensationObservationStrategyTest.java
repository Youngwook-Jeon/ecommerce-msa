package com.project.young.orderservice.messaging.observation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.compensation.CompensationRefundSla;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileCompensationObservationStrategyTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("MANUAL 보상을 JSONL 파일에 append한다")
    void emitManualCompensation_appendsJsonLine() throws Exception {
        Path file = tempDir.resolve("saga-compensation.jsonl");
        FileCompensationObservationStrategy strategy =
                new FileCompensationObservationStrategy(new ObjectMapper(), file.toString());

        SagaCompensationView view = new SagaCompensationView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user-1",
                "10.00",
                "USD",
                "payment.completed",
                "payment.completed.DLT",
                0,
                1L,
                "com.example.Ex",
                "failed",
                CompensationRecommendedAction.REFUND,
                CompensationRefundSla.IMMEDIATE,
                "inventory_unavailable_or_expired_refund_only",
                CompensationHandlingStatus.MANUAL,
                Instant.parse("2026-08-23T12:00:00Z"),
                true
        );

        strategy.emitManualCompensation(view);

        String line = Files.readString(file).trim();
        JsonNode node = new ObjectMapper().readTree(line);
        assertThat(node.get("type").asText()).isEqualTo("saga_compensation_manual");
        assertThat(node.get("orderId").asText()).isEqualTo(view.orderId().toString());
        assertThat(node.get("recommendedAction").asText()).isEqualTo("REFUND");
        assertThat(node.get("refundSla").asText()).isEqualTo("IMMEDIATE");
        assertThat(node.get("handlingStatus").asText()).isEqualTo("MANUAL");
    }
}
