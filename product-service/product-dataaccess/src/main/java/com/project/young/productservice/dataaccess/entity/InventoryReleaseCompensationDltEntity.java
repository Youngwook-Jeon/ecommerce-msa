package com.project.young.productservice.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_release_compensation_dlts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReleaseCompensationDltEntity {

    @Id
    @Column(name = "compensation_event_id", nullable = false, columnDefinition = "UUID")
    private UUID compensationEventId;

    @Column(name = "order_id", nullable = false, columnDefinition = "UUID")
    private UUID orderId;

    @Column(name = "source_topic", nullable = false, length = 255)
    private String sourceTopic;

    @Column(name = "dlt_topic", nullable = false, length = 255)
    private String dltTopic;

    @Column(name = "source_partition")
    private Integer sourcePartition;

    @Column(name = "source_offset")
    private Long sourceOffset;

    @Column(name = "failure_exception_class", length = 1024)
    private String failureExceptionClass;

    @Column(name = "failure_message", length = 4096)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
