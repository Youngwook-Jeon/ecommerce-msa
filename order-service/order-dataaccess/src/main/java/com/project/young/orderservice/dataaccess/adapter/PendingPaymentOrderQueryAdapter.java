package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.dto.PendingPaymentOrderView;
import com.project.young.orderservice.application.port.output.PendingPaymentOrderQueryPort;
import com.project.young.orderservice.dataaccess.enums.OrderStatusEntity;
import com.project.young.orderservice.dataaccess.repository.OrderJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
@Transactional(readOnly = true)
public class PendingPaymentOrderQueryAdapter implements PendingPaymentOrderQueryPort {

    private static final int MAX_BATCH_SIZE = 100;

    private final OrderJpaRepository orderJpaRepository;

    public PendingPaymentOrderQueryAdapter(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public List<PendingPaymentOrderView> findUpdatedBefore(Instant threshold, int limit) {
        if (threshold == null) {
            throw new IllegalArgumentException("threshold must not be null");
        }
        int pageSize = Math.clamp(limit, 1, MAX_BATCH_SIZE);
        return orderJpaRepository.findPendingPaymentUpdatedBefore(
                        OrderStatusEntity.PENDING_PAYMENT,
                        threshold,
                        PageRequest.of(0, pageSize))
                .stream()
                .map(order -> new PendingPaymentOrderView(
                        order.getId(), order.getUserId(), order.getUpdatedAt()))
                .toList();
    }
}
