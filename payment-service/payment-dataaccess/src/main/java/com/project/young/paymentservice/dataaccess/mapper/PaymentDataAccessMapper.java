package com.project.young.paymentservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.dataaccess.entity.PaymentEntity;
import com.project.young.paymentservice.dataaccess.enums.PaymentStatusEntity;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentDataAccessMapper {

    public PaymentEntity paymentToPaymentEntity(Payment payment) {
        return PaymentEntity.builder()
                .id(payment.getId().getValue())
                .orderId(payment.getOrderId().getValue())
                .userId(payment.getUserId().value())
                .amount(payment.getAmount().getAmount())
                .currency(payment.getCurrency())
                .status(toEntityStatus(payment.getStatus()))
                .failureReason(payment.getFailureReason())
                .provider(payment.getProvider() == null ? null : payment.getProvider().name())
                .providerPaymentId(payment.getProviderPaymentId())
                .clientSecret(payment.getClientSecret())
                .build();
    }

    public PaymentStatusEntity toEntityStatus(PaymentStatus domainStatus) {
        return PaymentStatusEntity.valueOf(domainStatus.name());
    }

    public PaymentStatus toDomainStatus(PaymentStatusEntity entityStatus) {
        return PaymentStatus.valueOf(entityStatus.name());
    }
}
