package com.project.young.productservice.dataaccess.entity;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;
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
@Table(name = "product_search_index_versions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchIndexVersionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "version_number", nullable = false)
    private long versionNumber;

    @Column(name = "index_name", nullable = false, length = 255)
    private String indexName;

    @Column(name = "mapping_version", nullable = false)
    private int mappingVersion;

    @Column(name = "mapping_hash", nullable = false, length = 64)
    private String mappingHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductSearchIndexStatus status;

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "activated_at", nullable = true)
    private Instant activatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;
}
