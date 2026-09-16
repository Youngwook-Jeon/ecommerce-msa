package com.project.young.orderservice.it.support;

import com.project.young.orderservice.application.dto.PaymentStatusSnapshot;
import com.project.young.orderservice.application.port.output.PaymentStatusQueryPort;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-process stand-in for Payment's batch status API in Order's container-backed integration tests. */
public final class PaymentStatusQueryTestHolder implements PaymentStatusQueryPort {

    private final Map<UUID, PaymentStatusSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Map<UUID, PaymentStatusSnapshot> findByOrderIds(Collection<UUID> orderIds) {
        return orderIds.stream()
                .filter(snapshots::containsKey)
                .collect(java.util.stream.Collectors.toMap(orderId -> orderId, snapshots::get));
    }

    public void put(PaymentStatusSnapshot snapshot) {
        snapshots.put(snapshot.orderId(), snapshot);
    }

    public void clear() {
        snapshots.clear();
    }
}
