package com.project.young.orderservice.dataaccess.entity;

import com.project.young.orderservice.domain.valueobject.CustomerRefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_refunds")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRefundEntity {

    @Id
    @Column(name = "refund_id", columnDefinition = "UUID")
    private UUID refundId;

    @Column(name = "order_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID orderId;

    @Column(name = "payment_id", nullable = false, columnDefinition = "UUID")
    private UUID paymentId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CustomerRefundStatus status;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @CreatedDate
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CustomerRefundEntity that)) {
            return false;
        }
        return refundId != null && refundId.equals(that.refundId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
