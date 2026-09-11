package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.dataaccess.mapper.PaymentAggregateMapper;
import com.project.young.paymentservice.dataaccess.mapper.PaymentDataAccessMapper;
import com.project.young.paymentservice.dataaccess.repository.PaymentJpaRepository;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRepositoryImplTest {

    @Test
    void findPendingWithProviderSessionUpdatedBefore_limitsTheDatabaseQueryPageSize() {
        PaymentJpaRepository repository = mock(PaymentJpaRepository.class);
        PaymentDataAccessMapper dataAccessMapper = mock(PaymentDataAccessMapper.class);
        PaymentAggregateMapper aggregateMapper = mock(PaymentAggregateMapper.class);
        PaymentRepositoryImpl adapter = new PaymentRepositoryImpl(
                repository, dataAccessMapper, aggregateMapper, mock(EntityManager.class));
        Instant threshold = Instant.parse("2026-01-01T00:00:00Z");
        when(dataAccessMapper.toEntityStatus(PaymentStatus.PENDING))
                .thenReturn(com.project.young.paymentservice.dataaccess.enums.PaymentStatusEntity.PENDING);
        when(repository.findPendingWithProviderSessionUpdatedBefore(any(), eq(threshold), any(Pageable.class)))
                .thenReturn(List.of());

        adapter.findPendingWithProviderSessionUpdatedBefore(threshold, 999);

        verify(repository).findPendingWithProviderSessionUpdatedBefore(
                eq(com.project.young.paymentservice.dataaccess.enums.PaymentStatusEntity.PENDING),
                eq(threshold),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 100)
        );
    }
}
