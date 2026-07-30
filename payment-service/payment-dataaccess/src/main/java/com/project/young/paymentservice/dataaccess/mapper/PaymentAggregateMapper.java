package com.project.young.paymentservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.dataaccess.entity.PaymentEntity;
import com.project.young.paymentservice.dataaccess.enums.PaymentStatusEntity;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;
import com.project.young.paymentservice.domain.valueobject.UserId;
import org.springframework.stereotype.Component;

@Component
public class PaymentAggregateMapper {

    public Payment toPayment(PaymentEntity entity) {
        if (entity == null) {
            return null;
        }

        return Payment.reconstitute(
                new PaymentId(entity.getId()),
                new OrderId(entity.getOrderId()),
                new UserId(entity.getUserId()),
                new Money(entity.getAmount()),
                entity.getCurrency(),
                toDomainStatus(entity.getStatus()),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private PaymentStatus toDomainStatus(PaymentStatusEntity entityStatus) {
        return PaymentStatus.valueOf(entityStatus.name());
    }
}
