package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.entity.OrderOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderOutboxJpaRepository extends JpaRepository<OrderOutboxEntity, UUID> {
}
