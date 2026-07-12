package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.entity.OrderEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<OrderEntity> findWithLinesById(UUID id);

    @EntityGraph(attributePaths = "lines")
    Optional<OrderEntity> findWithLinesByIdAndUserId(UUID id, String userId);
}
