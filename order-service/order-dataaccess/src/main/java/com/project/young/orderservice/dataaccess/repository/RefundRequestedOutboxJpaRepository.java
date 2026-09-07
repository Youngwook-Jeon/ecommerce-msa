package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.entity.RefundRequestedOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefundRequestedOutboxJpaRepository extends JpaRepository<RefundRequestedOutboxEntity, UUID> {

    boolean existsByCompensationEventId(UUID compensationEventId);
}
