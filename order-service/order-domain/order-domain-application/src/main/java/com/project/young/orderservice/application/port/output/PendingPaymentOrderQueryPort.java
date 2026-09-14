package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.dto.PendingPaymentOrderView;

import java.time.Instant;
import java.util.List;

public interface PendingPaymentOrderQueryPort {

    List<PendingPaymentOrderView> findUpdatedBefore(Instant threshold, int limit);
}
