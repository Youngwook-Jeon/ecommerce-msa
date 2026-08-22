package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.PaymentProviderEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProviderEventJpaRepository extends JpaRepository<PaymentProviderEventEntity, String> {
}
