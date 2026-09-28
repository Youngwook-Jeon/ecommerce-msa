package com.project.young.paymentservice.dataaccess.entity;

import com.project.young.paymentservice.application.refund.CustomerRefundDltStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_refund_dlts")
@Getter
public class CustomerRefundDltEntity {
    @Id
    private UUID id;
    @Column(name = "dlt_topic", nullable = false)
    private String dltTopic;
    @Column(name = "dlt_partition", nullable = false)
    private int dltPartition;
    @Column(name = "dlt_offset", nullable = false)
    private long dltOffset;
    @Column(name = "message_key", columnDefinition = "text")
    private String messageKey;
    @Column(columnDefinition = "text")
    private String payload;
    @Column(name = "source_topic")
    private String sourceTopic;
    @Column(name = "source_partition")
    private Integer sourcePartition;
    @Column(name = "source_offset")
    private Long sourceOffset;
    @Column(name = "exception_class", columnDefinition = "text")
    private String exceptionClass;
    @Column(name = "exception_message", columnDefinition = "text")
    private String exceptionMessage;
    @Column(name = "handling_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private CustomerRefundDltStatus handlingStatus;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CustomerRefundDltEntity() {
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CustomerRefundDltEntity that
                && id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
