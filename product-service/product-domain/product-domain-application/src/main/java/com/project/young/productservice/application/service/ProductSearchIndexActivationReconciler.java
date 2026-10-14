package com.project.young.productservice.application.service;

import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexReconciliationPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexAliasState;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexReconciliationAttempt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INVALID_INDEX_MANAGEMENT_REQUEST;

@Slf4j
public class ProductSearchIndexActivationReconciler {
    private final ProductSearchIndexReconciliationPort store;
    private final ProductSearchIndexActivationWorkPort activations;
    private final ProductSearchIndexAliasPort aliases;
    private final Duration lease;
    private final Duration retryDelay;

    public ProductSearchIndexActivationReconciler(ProductSearchIndexReconciliationPort store,
            ProductSearchIndexActivationWorkPort activations, ProductSearchIndexAliasPort aliases, Duration lease, Duration retryDelay) {
        this.store = store;
        this.activations = activations;
        this.aliases = aliases;
        this.lease = lease;
        this.retryDelay = retryDelay;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runOnce() {
        recoverExpired();
        store.claimNext(lease, retryDelay).ifPresent(this::observe);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProductSearchIndexReconciliationAttempt recheck(UUID jobId, String requestedBy) {
        if (jobId == null || requestedBy == null || requestedBy.isBlank() || requestedBy.length() > 255) {
            throw new ProductSearchIndexManagementException(INVALID_INDEX_MANAGEMENT_REQUEST, "Job and authenticated administrator are required");
        }
        recoverExpired();
        return observe(store.claim(jobId, requestedBy, lease));
    }

    public Optional<ProductSearchIndexReconciliationAttempt> latest(UUID jobId) {
        return store.latest(jobId);
    }

    private void recoverExpired() {
        activations.recoverExpired();
        store.recoverExpired();
    }

    private ProductSearchIndexReconciliationAttempt observe(ProductSearchIndexReconciliationPort.Work work) {
        ProductSearchIndexAliasState observed = null;
        try {
            observed = aliases.readAlias(work.job().aliasName());
        } catch (RuntimeException exception) {
            log.warn("Reconciliation alias lookup failed: jobId={}, attemptId={}, alias={}",
                    work.job().id(), work.attemptId(), work.job().aliasName(), exception);
        }
        // A DB failure must propagate: it must not be mistaken for an OpenSearch lookup failure.
        return store.finish(work, observed);
    }
}
