package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.dto.event.OrderCreatedEvent;
import com.project.young.orderservice.application.port.output.OrderOutboxPort;
import com.project.young.orderservice.dataaccess.entity.OrderOutboxEntity;
import com.project.young.orderservice.dataaccess.repository.OrderOutboxJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class OrderOutboxAdapter implements OrderOutboxPort {

    private final OrderOutboxJpaRepository orderOutboxJpaRepository;

    public OrderOutboxAdapter(OrderOutboxJpaRepository orderOutboxJpaRepository) {
        this.orderOutboxJpaRepository = orderOutboxJpaRepository;
    }

    @Override
    public void enqueueCreated(OrderCreatedEvent event) {
        orderOutboxJpaRepository.save(OrderOutboxEntity.builder()
                .eventId(event.eventId())
                .orderId(event.orderId())
                .userId(event.userId())
                .totalAmount(event.totalAmount().getAmount())
                .currency(event.currency())
                .occurredAt(event.occurredAt())
                .build());
    }
}
