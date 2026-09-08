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
@Table(name = "inventory_release_compensations")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReleaseCompensationEntity {

    @Id
    @Column(name = "compensation_event_id", nullable = false, columnDefinition = "UUID")
    private UUID compensationEventId;

    @Column(name = "order_id", nullable = false, columnDefinition = "UUID")
    private UUID orderId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
