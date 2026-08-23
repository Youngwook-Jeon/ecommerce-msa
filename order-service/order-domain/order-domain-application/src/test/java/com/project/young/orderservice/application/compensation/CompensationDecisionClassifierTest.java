package com.project.young.orderservice.application.compensation;

import com.project.young.orderservice.application.port.output.InventoryReservationClientException;
import com.project.young.orderservice.application.port.output.InventoryReservationConflictException;
import com.project.young.orderservice.domain.exception.OrderIllegalTransitionException;
import com.project.young.orderservice.domain.exception.OrderNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompensationDecisionClassifierTest {

    @Test
    @DisplayName("재고 conflict → REFUND + IMMEDIATE (환불만)")
    void inventoryConflict_refundImmediate() {
        CompensationDecision decision = CompensationDecisionClassifier.classify(
                InventoryReservationConflictException.class.getName(),
                "409 Conflict"
        );

        assertThat(decision.recommendedAction()).isEqualTo(CompensationRecommendedAction.REFUND);
        assertThat(decision.refundSla()).isEqualTo(CompensationRefundSla.IMMEDIATE);
        assertThat(decision.reason()).contains("refund_only");
    }

    @Test
    @DisplayName("재고 client 4xx(만료 포함) → REFUND + IMMEDIATE")
    void inventoryClient_refundImmediate() {
        CompensationDecision decision = CompensationDecisionClassifier.classify(
                InventoryReservationClientException.class.getName(),
                "rejected with status 410"
        );

        assertThat(decision.recommendedAction()).isEqualTo(CompensationRecommendedAction.REFUND);
        assertThat(decision.refundSla()).isEqualTo(CompensationRefundSla.IMMEDIATE);
    }

    @Test
    @DisplayName("불법 상태전이 → REFUND + IMMEDIATE")
    void illegalTransition_refundImmediate() {
        CompensationDecision decision = CompensationDecisionClassifier.classify(
                OrderIllegalTransitionException.class.getName(),
                "cannot confirm CANCELLED"
        );

        assertThat(decision.recommendedAction()).isEqualTo(CompensationRecommendedAction.REFUND);
        assertThat(decision.refundSla()).isEqualTo(CompensationRefundSla.IMMEDIATE);
    }

    @Test
    @DisplayName("주문 없음 → MANUAL")
    void orderNotFound_manual() {
        CompensationDecision decision = CompensationDecisionClassifier.classify(
                OrderNotFoundException.class.getName(),
                "missing"
        );

        assertThat(decision.recommendedAction()).isEqualTo(CompensationRecommendedAction.MANUAL);
        assertThat(decision.refundSla()).isEqualTo(CompensationRefundSla.NONE);
    }

    @Test
    @DisplayName("기타 예외(재시도 소진) → REPLAY 권고")
    void unknownTransient_replay() {
        CompensationDecision decision = CompensationDecisionClassifier.classify(
                "java.net.SocketTimeoutException",
                "timed out"
        );

        assertThat(decision.recommendedAction()).isEqualTo(CompensationRecommendedAction.REPLAY);
        assertThat(decision.refundSla()).isEqualTo(CompensationRefundSla.NONE);
    }
}
