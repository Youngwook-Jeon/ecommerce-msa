package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.compensation.CompensationDecision;
import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.compensation.CompensationRefundSla;
import com.project.young.orderservice.application.dto.compensation.RecordManualCompensationCommand;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.dataaccess.entity.SagaCompensationEntity;
import com.project.young.orderservice.dataaccess.repository.SagaCompensationJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaCompensationAdapterTest {

    @Mock
    private SagaCompensationJpaRepository sagaCompensationJpaRepository;

    @InjectMocks
    private SagaCompensationAdapter adapter;

    @Test
    @DisplayName("insertManual: 커맨드/결정을 entity에 매핑해 저장한다")
    void insertManual_mapsAndSaves() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RecordManualCompensationCommand command = new RecordManualCompensationCommand(
                eventId,
                UUID.randomUUID(),
                orderId,
                "user-1",
                "12.00",
                "USD",
                "payment.completed",
                "payment.completed.DLT",
                0,
                7L,
                "com.ex.Fail",
                "boom"
        );
        CompensationDecision decision = new CompensationDecision(
                CompensationRecommendedAction.REFUND,
                CompensationRefundSla.IMMEDIATE,
                "inventory_unavailable_or_expired_refund_only"
        );

        when(sagaCompensationJpaRepository.save(any())).thenAnswer(invocation -> {
            SagaCompensationEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.parse("2026-08-23T12:00:00Z"));
            return entity;
        });

        SagaCompensationView view = adapter.insertManual(
                command,
                decision,
                CompensationHandlingStatus.MANUAL
        );

        ArgumentCaptor<SagaCompensationEntity> captor = ArgumentCaptor.forClass(SagaCompensationEntity.class);
        verify(sagaCompensationJpaRepository).save(captor.capture());
        SagaCompensationEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getRecommendedAction()).isEqualTo("REFUND");
        assertThat(saved.getRefundSla()).isEqualTo("IMMEDIATE");
        assertThat(saved.getHandlingStatus()).isEqualTo("MANUAL");
        assertThat(view.newlyCreated()).isTrue();
        assertThat(view.recommendedAction()).isEqualTo(CompensationRecommendedAction.REFUND);
    }
}
