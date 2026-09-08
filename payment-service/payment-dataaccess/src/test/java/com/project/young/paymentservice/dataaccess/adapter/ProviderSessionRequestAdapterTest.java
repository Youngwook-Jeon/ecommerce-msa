package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.provider.ProviderSessionRequestStatus;
import com.project.young.paymentservice.dataaccess.entity.ProviderSessionRequestEntity;
import com.project.young.paymentservice.dataaccess.repository.ProviderSessionRequestJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderSessionRequestAdapterTest {

    @Test
    void claimPending_claimsOnlyRequestsStillPending() {
        ProviderSessionRequestJpaRepository repository = mock(ProviderSessionRequestJpaRepository.class);
        ProviderSessionRequestAdapter adapter = new ProviderSessionRequestAdapter(repository);
        UUID claimedId = UUID.randomUUID();
        UUID alreadyClaimedId = UUID.randomUUID();
        when(repository.findTop100ByStatusOrderByCreatedAt(ProviderSessionRequestStatus.PENDING))
                .thenReturn(List.of(entity(claimedId), entity(alreadyClaimedId)));
        when(repository.claim(eq(claimedId), eq("PENDING"), eq("PROCESSING"), any())).thenReturn(1);
        when(repository.claim(eq(alreadyClaimedId), eq("PENDING"), eq("PROCESSING"), any())).thenReturn(0);

        List<UUID> claimed = adapter.claimPending(100);

        assertThat(claimed).containsExactly(claimedId);
        verify(repository).findTop100ByStatusOrderByCreatedAt(ProviderSessionRequestStatus.PENDING);
    }

    @Test
    void enqueue_usesPendingStatus() {
        ProviderSessionRequestJpaRepository repository = mock(ProviderSessionRequestJpaRepository.class);
        ProviderSessionRequestAdapter adapter = new ProviderSessionRequestAdapter(repository);
        UUID paymentId = UUID.randomUUID();

        adapter.enqueue(paymentId);

        verify(repository).enqueue(eq(paymentId), eq(ProviderSessionRequestStatus.PENDING.name()), any());
    }

    private static ProviderSessionRequestEntity entity(UUID paymentId) {
        return ProviderSessionRequestEntity.builder()
                .paymentId(paymentId)
                .status(ProviderSessionRequestStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
