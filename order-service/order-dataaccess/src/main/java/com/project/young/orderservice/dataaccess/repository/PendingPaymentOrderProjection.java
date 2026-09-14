package com.project.young.orderservice.dataaccess.repository;

import java.time.Instant;
import java.util.UUID;

public interface PendingPaymentOrderProjection {

    UUID getId();

    String getUserId();

    Instant getUpdatedAt();
}
