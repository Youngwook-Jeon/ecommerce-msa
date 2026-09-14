package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.compensation.OrderCreatedDltStatus;
import com.project.young.paymentservice.application.dto.query.OrderCreatedDltOperationsView;
import com.project.young.paymentservice.application.port.output.OrderCreatedDltPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderCreatedDltOperationsQueryService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final OrderCreatedDltPort queue;

    public OrderCreatedDltOperationsQueryService(OrderCreatedDltPort queue) {
        this.queue = queue;
    }

    @Transactional(readOnly = true)
    public List<OrderCreatedDltOperationsView> getItems(OrderCreatedDltStatus status, int requestedLimit) {
        int limit = requestedLimit <= 0 ? DEFAULT_LIMIT : Math.min(requestedLimit, MAX_LIMIT);
        return queue.findForOperations(status, limit);
    }
}
