package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.entity.CartEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartJpaRepository extends JpaRepository<CartEntity, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<CartEntity> findByUserId(String userId);

    @EntityGraph(attributePaths = "items")
    Optional<CartEntity> findWithItemsById(UUID id);
}
