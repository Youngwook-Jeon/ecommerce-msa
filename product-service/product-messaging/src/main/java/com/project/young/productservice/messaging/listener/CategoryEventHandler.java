package com.project.young.productservice.messaging.listener;

import com.project.young.productservice.application.service.ProductIntegrationService;
import com.project.young.productservice.domain.event.CategoryDeletedEvent;
import com.project.young.productservice.domain.event.CategoryStatusChangedEvent;
import com.project.young.productservice.domain.service.ProductDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class CategoryEventHandler {

    private final ProductIntegrationService productIntegrationService;

    public CategoryEventHandler(ProductIntegrationService productIntegrationService) {
        this.productIntegrationService = productIntegrationService;
    }

    @Async
    @EventListener
    @Transactional
    public void handleCategoryStatusChanged(CategoryStatusChangedEvent event) {
        try {
            productIntegrationService.handleCategoryStatusChanged(
                    event.getCategoryId(),
                    event.getOldStatus(),
                    event.getNewStatus()
            );
        } catch (Exception e) {
            log.error("Failed to handle category status changed event", e);
            throw e;
        }
    }
}