package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.entity.SagaCompensationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SagaCompensationJpaRepository extends JpaRepository<SagaCompensationEntity, UUID> {

    Optional<SagaCompensationEntity> findByEventId(UUID eventId);
}
