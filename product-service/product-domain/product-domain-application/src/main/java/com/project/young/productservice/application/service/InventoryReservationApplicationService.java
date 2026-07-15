package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CheckoutId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.application.config.InventoryReservationProperties;
import com.project.young.productservice.application.dto.command.ReserveInventoryCommand;
import com.project.young.productservice.application.dto.result.ReserveInventoryResult;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.port.output.InventoryVariantStockPort;
import com.project.young.productservice.application.port.output.InventoryVariantStockPort.VariantStockSnapshot;
import com.project.young.productservice.application.support.InventoryReservationTxExecutor;
import com.project.young.productservice.domain.entity.InventoryReservation;
import com.project.young.productservice.domain.exception.InventoryDomainException;
import com.project.young.productservice.domain.exception.InventoryReservationNotFoundException;
import com.project.young.productservice.domain.inventory.InventoryAvailability;
import com.project.young.productservice.domain.repository.InventoryReservationRepository;
import com.project.young.productservice.domain.valueobject.InventoryReservationId;
import com.project.young.productservice.domain.valueobject.InventoryReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@EnableConfigurationProperties(InventoryReservationProperties.class)
public class InventoryReservationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationApplicationService.class);

    /**
     * Partial unique index from V8. Concurrent reserves for the same ACTIVE (checkout, variant)
     * may lose the race at insert time; retry so the loser can reuse or release+recreate.
     */
    static final String ACTIVE_CHECKOUT_VARIANT_UNIQUE_INDEX =
            "uk_inventory_reservations_active_checkout_variant";

    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryVariantStockPort inventoryVariantStockPort;
    private final IdGenerator idGenerator;
    private final InventoryReservationProperties properties;
    private final InventoryReservationTxExecutor txExecutor;

    public InventoryReservationApplicationService(
            InventoryReservationRepository inventoryReservationRepository,
            InventoryVariantStockPort inventoryVariantStockPort,
            IdGenerator idGenerator,
            InventoryReservationProperties properties,
            InventoryReservationTxExecutor txExecutor
    ) {
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.inventoryVariantStockPort = inventoryVariantStockPort;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.txExecutor = txExecutor;
    }

    public ReserveInventoryResult reserve(ReserveInventoryCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.checkoutId(), "checkoutId must not be null");
        validateLines(command.lines());

        return executeWithConcurrencyRetry(
                "reserving inventory for checkout " + command.checkoutId(),
                () -> txExecutor.executeInNewTransaction(() -> doReserve(command))
        );
    }

    private ReserveInventoryResult doReserve(ReserveInventoryCommand command) {
        CheckoutId checkoutId = new CheckoutId(command.checkoutId());
        Instant now = Instant.now();
        Map<ProductVariantId, Integer> requestedByVariant = toRequestedMap(command.lines());

        List<InventoryReservation> existing = inventoryReservationRepository.findByCheckoutId(checkoutId);
        if (existing.stream().anyMatch(r -> r.getStatus() == InventoryReservationStatus.CONFIRMED)) {
            throw new InventoryDomainException(
                    "Cannot reserve inventory for checkout that already has CONFIRMED reservations: "
                            + checkoutId.getValue());
        }

        List<InventoryReservation> activeExisting = existing.stream()
                .filter(r -> r.getStatus() == InventoryReservationStatus.ACTIVE)
                .toList();
        if (!activeExisting.isEmpty()) {
            if (sameLines(activeExisting, requestedByVariant)) {
                Instant expiresAt = activeExisting.stream()
                        .map(InventoryReservation::getExpiresAt)
                        .min(Instant::compareTo)
                        .orElseThrow();
                return ReserveInventoryResult.from(checkoutId.getValue(), expiresAt, activeExisting, true);
            }
            releaseActive(activeExisting, now);
            // Hibernate may flush inserts before updates; force ACTIVE→RELEASED to DB first
            // so the partial unique index allows a new ACTIVE row for the same checkout+variant.
            inventoryReservationRepository.flush();
        }

        List<ProductVariantId> orderedIds = requestedByVariant.keySet().stream()
                .sorted(Comparator.comparing(ProductVariantId::getValue))
                .toList();

        // Concurrency gate first: ordered version touch so concurrent reserves conflict early.
        inventoryVariantStockPort.touchVersions(orderedIds);

        List<VariantStockSnapshot> stocks = inventoryVariantStockPort.findOrderedByIds(orderedIds);
        Map<ProductVariantId, VariantStockSnapshot> stockById = stocks.stream()
                .collect(Collectors.toMap(VariantStockSnapshot::variantId, Function.identity()));

        Instant expiresAt = now.plus(properties.getReservationTtl());
        List<InventoryReservation> toInsert = new ArrayList<>();

        for (ProductVariantId variantId : orderedIds) {
            VariantStockSnapshot stock = stockById.get(variantId);
            if (stock == null) {
                throw new InventoryDomainException("Product variant not found: " + variantId.getValue());
            }
            if (!stock.reservable()) {
                throw new InventoryDomainException(
                        "Product variant is not reservable: " + variantId.getValue());
            }
            int requested = requestedByVariant.get(variantId);
            int activeReserved = inventoryReservationRepository.sumActiveQuantityByVariantId(variantId, now);
            InventoryAvailability.assertSufficient(variantId, stock.onHand(), activeReserved, requested);

            toInsert.add(InventoryReservation.createActive(
                    new InventoryReservationId(idGenerator.generateId()),
                    checkoutId,
                    variantId,
                    requested,
                    expiresAt,
                    now
            ));
        }

        inventoryReservationRepository.insertAll(toInsert);

        log.debug(
                "Reserved inventory for checkout {} (lines={}, expiresAt={})",
                checkoutId.getValue(),
                toInsert.size(),
                expiresAt
        );
        return ReserveInventoryResult.from(checkoutId.getValue(), expiresAt, toInsert, false);
    }

    public void confirm(UUID checkoutIdValue) {
        Objects.requireNonNull(checkoutIdValue, "checkoutId must not be null");
        executeWithConcurrencyRetry(
                "confirming inventory for checkout " + checkoutIdValue,
                () -> {
                    txExecutor.runInNewTransaction(() -> doConfirm(checkoutIdValue));
                    return null;
                }
        );
    }

    private void doConfirm(UUID checkoutIdValue) {
        CheckoutId checkoutId = new CheckoutId(checkoutIdValue);
        Instant now = Instant.now();
        List<InventoryReservation> reservations = inventoryReservationRepository.findByCheckoutId(checkoutId);
        if (reservations.isEmpty()) {
            throw new InventoryReservationNotFoundException(
                    "No inventory reservations for checkout: " + checkoutIdValue);
        }

        List<InventoryReservation> active = reservations.stream()
                .filter(r -> r.getStatus() == InventoryReservationStatus.ACTIVE)
                .toList();
        List<InventoryReservation> confirmed = reservations.stream()
                .filter(r -> r.getStatus() == InventoryReservationStatus.CONFIRMED)
                .toList();

        // RELEASED/EXPIRED rows may remain from prior re-reserves; ignore them for confirm gating.
        if (active.isEmpty() && !confirmed.isEmpty()) {
            return;
        }
        if (!active.isEmpty() && !confirmed.isEmpty()) {
            throw new InventoryDomainException(
                    "Inventory reservation state is inconsistent for checkout " + checkoutIdValue
                            + ": mixed ACTIVE and CONFIRMED rows.");
        }
        if (active.isEmpty()) {
            throw new InventoryDomainException(
                    "No ACTIVE inventory reservations to confirm for checkout: " + checkoutIdValue);
        }

        List<InventoryReservation> ordered = active.stream()
                .sorted(Comparator.comparing(r -> r.getProductVariantId().getValue()))
                .toList();

        for (InventoryReservation reservation : ordered) {
            if (!reservation.isActiveAt(now)) {
                throw new InventoryDomainException(
                        "Cannot confirm expired inventory reservation: " + reservation.getId().getValue());
            }
            // Soft-hold policy: a successfully reserved line remains confirmable even if the
            // catalog later marks the product/variant as discontinued or deleted.
            inventoryVariantStockPort.decreaseOnHandForConfirmedHold(
                    reservation.getProductVariantId(),
                    reservation.getQuantity()
            );
            reservation.confirm(now);
            inventoryReservationRepository.update(reservation);
        }

        log.debug("Confirmed inventory for checkout {} (lines={})", checkoutIdValue, ordered.size());
    }

    @Transactional
    public void release(UUID checkoutIdValue) {
        Objects.requireNonNull(checkoutIdValue, "checkoutId must not be null");
        CheckoutId checkoutId = new CheckoutId(checkoutIdValue);
        Instant now = Instant.now();
        List<InventoryReservation> reservations = inventoryReservationRepository.findByCheckoutId(checkoutId);
        if (reservations.isEmpty()) {
            throw new InventoryReservationNotFoundException(
                    "No inventory reservations for checkout: " + checkoutIdValue);
        }

        List<InventoryReservation> active = reservations.stream()
                .filter(r -> r.getStatus() == InventoryReservationStatus.ACTIVE)
                .toList();
        if (active.isEmpty()) {
            return;
        }

        releaseActive(active, now);
        log.debug("Released inventory for checkout {} (lines={})", checkoutIdValue, active.size());
    }

    @Transactional
    public int expireDueReservations(int batchSize) {
        Instant now = Instant.now();
        int limit = batchSize > 0 ? batchSize : properties.getExpireBatchSize();
        List<InventoryReservation> due = inventoryReservationRepository.findDueActiveForUpdate(now, limit);
        for (InventoryReservation reservation : due) {
            reservation.expire(now);
            inventoryReservationRepository.update(reservation);
        }
        if (!due.isEmpty()) {
            log.info("Expired {} inventory reservation(s)", due.size());
        }
        return due.size();
    }

    private void releaseActive(List<InventoryReservation> active, Instant now) {
        for (InventoryReservation reservation : active) {
            reservation.release(now);
            inventoryReservationRepository.update(reservation);
        }
    }

    private static boolean sameLines(
            List<InventoryReservation> activeExisting,
            Map<ProductVariantId, Integer> requested
    ) {
        Map<ProductVariantId, Integer> existing = activeExisting.stream()
                .collect(Collectors.toMap(
                        InventoryReservation::getProductVariantId,
                        InventoryReservation::getQuantity
                ));
        return requested.equals(existing);
    }

    private <T> T executeWithConcurrencyRetry(String actionLabel, SupplierWithException<T> action) {
        RuntimeException lastConflict = null;
        int maxAttempts = Math.max(1, properties.getMaxOptimisticAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (OptimisticLockingFailureException ex) {
                lastConflict = ex;
                log.warn(
                        "Optimistic lock conflict while {} (attempt {}/{})",
                        actionLabel,
                        attempt,
                        maxAttempts
                );
            } catch (DataIntegrityViolationException ex) {
                if (!isActiveCheckoutVariantUniqueViolation(ex)) {
                    throw ex;
                }
                lastConflict = ex;
                log.warn(
                        "Active checkout-variant unique conflict while {} (attempt {}/{})",
                        actionLabel,
                        attempt,
                        maxAttempts
                );
            }
        }
        throw lastConflict;
    }

    static boolean isActiveCheckoutVariantUniqueViolation(DataIntegrityViolationException ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains(ACTIVE_CHECKOUT_VARIANT_UNIQUE_INDEX)) {
                return true;
            }
        }
        return false;
    }

    private static Map<ProductVariantId, Integer> toRequestedMap(
            List<ReserveInventoryCommand.ReserveInventoryLine> lines
    ) {
        Map<ProductVariantId, Integer> requested = new HashMap<>();
        for (ReserveInventoryCommand.ReserveInventoryLine line : lines) {
            requested.put(new ProductVariantId(line.productVariantId()), line.quantity());
        }
        return requested;
    }

    private static void validateLines(List<ReserveInventoryCommand.ReserveInventoryLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new InventoryDomainException("Reserve request must contain at least one line.");
        }
        Set<UUID> seen = new HashSet<>();
        for (ReserveInventoryCommand.ReserveInventoryLine line : lines) {
            if (line.productVariantId() == null) {
                throw new InventoryDomainException("productVariantId must not be null.");
            }
            if (line.quantity() <= 0) {
                throw new InventoryDomainException("quantity must be positive.");
            }
            if (!seen.add(line.productVariantId())) {
                throw new InventoryDomainException(
                        "Duplicate productVariantId in reserve request: " + line.productVariantId());
            }
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
