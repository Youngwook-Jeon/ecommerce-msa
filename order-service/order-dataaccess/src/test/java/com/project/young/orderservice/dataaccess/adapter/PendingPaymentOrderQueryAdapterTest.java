package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.dataaccess.enums.OrderStatusEntity;
import com.project.young.orderservice.dataaccess.repository.OrderJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingPaymentOrderQueryAdapterTest {

    @Test
    void findUpdatedBefore_limitsTheDatabaseQueryPageSize() {
        OrderJpaRepository repository = mock(OrderJpaRepository.class);
        PendingPaymentOrderQueryAdapter adapter = new PendingPaymentOrderQueryAdapter(repository);
        Instant threshold = Instant.parse("2026-09-14T00:00:00Z");
        when(repository.findPendingPaymentUpdatedBefore(
                eq(OrderStatusEntity.PENDING_PAYMENT), eq(threshold), any(Pageable.class)))
                .thenReturn(List.of());

        adapter.findUpdatedBefore(threshold, 999);

        verify(repository).findPendingPaymentUpdatedBefore(
                eq(OrderStatusEntity.PENDING_PAYMENT),
                eq(threshold),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 100)
        );
    }
}
