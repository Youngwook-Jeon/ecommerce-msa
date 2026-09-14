package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.dto.PaymentStatusSnapshot;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface PaymentStatusQueryPort {

    Map<UUID, PaymentStatusSnapshot> findByOrderIds(Collection<UUID> orderIds);
}
