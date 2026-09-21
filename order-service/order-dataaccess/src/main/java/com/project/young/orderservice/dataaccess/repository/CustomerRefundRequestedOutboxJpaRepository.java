package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.entity.CustomerRefundRequestedOutboxEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerRefundRequestedOutboxJpaRepository extends JpaRepository<CustomerRefundRequestedOutboxEntity, UUID> {
}
