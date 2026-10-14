package com.project.young.productservice.dataaccess.entity;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasObservation;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexReconciliationStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_search_index_reconciliation_attempts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchIndexReconciliationAttemptEntity {
    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "requested_by", length = 255)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductSearchIndexReconciliationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "lease_until", nullable = false)
    private Instant leaseUntil;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ProductSearchIndexAliasObservation observation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "observed_indices", columnDefinition = "jsonb")
    private List<String> observedIndices;

    @Column(name = "alias_plain")
    private Boolean aliasPlain;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;
}
