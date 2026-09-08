package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.entity.InventoryReleaseRequestedOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryReleaseRequestedOutboxJpaRepository
        extends JpaRepository<InventoryReleaseRequestedOutboxEntity, UUID> {

    boolean existsByCompensationEventId(UUID compensationEventId);
}
