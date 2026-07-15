package com.project.young.productservice.application.service;

import com.project.young.productservice.application.config.InventoryReservationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservationExpireSchedulerTest {

    @Mock
    private InventoryReservationApplicationService inventoryReservationApplicationService;

    private InventoryReservationProperties properties;
    private InventoryReservationExpireScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new InventoryReservationProperties();
        properties.setExpireBatchSize(100);
        scheduler = new InventoryReservationExpireScheduler(
                inventoryReservationApplicationService,
                properties
        );
    }

    @Test
    @DisplayName("expireDueReservations: 설정된 batch size로 application service에 위임한다")
    void expireDueReservations_delegatesWithConfiguredBatchSize() {
        when(inventoryReservationApplicationService.expireDueReservations(100)).thenReturn(3);

        scheduler.expireDueReservations();

        verify(inventoryReservationApplicationService).expireDueReservations(100);
    }
}
