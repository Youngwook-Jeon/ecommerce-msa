package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.compensation.CompensationDecision;
import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.compensation.CompensationRefundSla;
import com.project.young.orderservice.application.dto.compensation.RecordManualCompensationCommand;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.port.output.SagaCompensationPort;
import com.project.young.orderservice.dataaccess.entity.SagaCompensationEntity;
import com.project.young.orderservice.dataaccess.repository.SagaCompensationJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class SagaCompensationAdapter implements SagaCompensationPort {

    private static final int MAX_FAILURE_MESSAGE = 2000;

    private final SagaCompensationJpaRepository sagaCompensationJpaRepository;

    public SagaCompensationAdapter(SagaCompensationJpaRepository sagaCompensationJpaRepository) {
        this.sagaCompensationJpaRepository = sagaCompensationJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SagaCompensationView> findByEventId(UUID eventId) {
        return sagaCompensationJpaRepository.findByEventId(eventId)
                .map(entity -> toView(entity, false));
    }

    @Override
    public SagaCompensationView insertManual(
            RecordManualCompensationCommand command,
            CompensationDecision decision,
            CompensationHandlingStatus handlingStatus
    ) {
        SagaCompensationEntity saved = sagaCompensationJpaRepository.save(SagaCompensationEntity.builder()
                .eventId(command.eventId())
                .paymentId(command.paymentId())
                .orderId(command.orderId())
                .userId(command.userId())
                .amount(command.amount())
                .currency(command.currency())
                .sourceTopic(command.sourceTopic())
                .dltTopic(command.dltTopic())
                .sourcePartition(command.sourcePartition())
                .sourceOffset(command.sourceOffset())
                .failureExceptionClass(truncate(command.failureExceptionClass(), 512))
                .failureMessage(truncate(command.failureMessage(), MAX_FAILURE_MESSAGE))
                .recommendedAction(decision.recommendedAction().name())
                .refundSla(decision.refundSla().name())
                .classificationReason(decision.reason())
                .handlingStatus(handlingStatus.name())
                .build());
        return toView(saved, true);
    }

    private static SagaCompensationView toView(SagaCompensationEntity entity, boolean newlyCreated) {
        return new SagaCompensationView(
                entity.getId(),
                entity.getEventId(),
                entity.getPaymentId(),
                entity.getOrderId(),
                entity.getUserId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getSourceTopic(),
                entity.getDltTopic(),
                entity.getSourcePartition(),
                entity.getSourceOffset(),
                entity.getFailureExceptionClass(),
                entity.getFailureMessage(),
                CompensationRecommendedAction.valueOf(entity.getRecommendedAction()),
                CompensationRefundSla.valueOf(entity.getRefundSla()),
                entity.getClassificationReason(),
                CompensationHandlingStatus.valueOf(entity.getHandlingStatus()),
                entity.getCreatedAt(),
                newlyCreated
        );
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
