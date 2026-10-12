package com.project.young.productservice.dataaccess.entity;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_search_index_build_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchIndexBuildJobEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "index_version_id", nullable = false)
    private UUID indexVersionId;

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "batch_size", nullable = false)
    private int batchSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductSearchIndexBuildStatus status;

    @Column(name = "source_product_count", nullable = true)
    private Long sourceProductCount;

    @Column(name = "indexed_products", nullable = false)
    private long indexedProducts;

    @Column(name = "completed_batches", nullable = false)
    private long completedBatches;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at", nullable = true)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = true)
    private Instant finishedAt;

    @Column(name = "failure_code", nullable = true, length = 64)
    private String failureCode;

    @Column(name = "failure_message", nullable = true, length = 500)
    private String failureMessage;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;
}
