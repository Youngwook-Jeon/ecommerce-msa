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
import com.project.young.productservice.domain.exception.InsufficientInventoryException;
import com.project.young.productservice.domain.exception.InventoryDomainException;
import com.project.young.productservice.domain.exception.InventoryReservationNotFoundException;
import com.project.young.productservice.domain.repository.InventoryReservationRepository;
import com.project.young.productservice.domain.valueobject.InventoryReservationId;
import com.project.young.productservice.domain.valueobject.InventoryReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservationApplicationServiceTest {

    private static final UUID CHECKOUT_ID = UUID.randomUUID();
    private static final UUID VARIANT_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

    @Mock
    private InventoryVariantStockPort inventoryVariantStockPort;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private InventoryReservationTxExecutor txExecutor;

    private InventoryReservationProperties properties;
    private InventoryReservationApplicationService service;

    @BeforeEach
    void setUp() {
        properties = new InventoryReservationProperties();
        properties.setReservationTtl(Duration.ofMinutes(15));
        properties.setMaxOptimisticAttempts(3);
        properties.setExpireBatchSize(100);
        service = new InventoryReservationApplicationService(
                inventoryReservationRepository,
                inventoryVariantStockPort,
                idGenerator,
                properties,
                txExecutor
        );

        // Shared tx wiring: reserve uses execute*, confirm uses run*; other tests use neither.
        // Use doAnswer (not when().thenAnswer) so later re-stubbing does not invoke this answer with a null arg.
        lenient().doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        }).when(txExecutor).executeInNewTransaction(any());
        lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(txExecutor).runInNewTransaction(any());
    }

    @Test
    @DisplayName("reserve: version touch 후 available 검사하고 insert한다")
    void reserve_touchesVersionsBeforeInsert() {
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of());
        when(inventoryVariantStockPort.findOrderedByIds(any()))
                .thenReturn(List.of(new VariantStockSnapshot(
                        new ProductVariantId(VARIANT_ID), 5, true)));
        when(inventoryReservationRepository.sumActiveQuantityByVariantId(any(), any())).thenReturn(0);
        when(idGenerator.generateId()).thenReturn(RESERVATION_ID);

        ReserveInventoryResult result = service.reserve(command(1));

        assertThat(result.reusedExisting()).isFalse();
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().status()).isEqualTo("ACTIVE");

        InOrder inOrder = inOrder(inventoryVariantStockPort, inventoryReservationRepository);
        inOrder.verify(inventoryVariantStockPort).touchVersions(any());
        inOrder.verify(inventoryVariantStockPort).findOrderedByIds(any());
        inOrder.verify(inventoryReservationRepository).insertAll(any());
    }

    @Test
    @DisplayName("reserve: available 부족이면 InsufficientInventoryException")
    void reserve_insufficient_throws() {
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of());
        when(inventoryVariantStockPort.findOrderedByIds(any()))
                .thenReturn(List.of(new VariantStockSnapshot(
                        new ProductVariantId(VARIANT_ID), 1, true)));
        when(inventoryReservationRepository.sumActiveQuantityByVariantId(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.reserve(command(2)))
                .isInstanceOf(InsufficientInventoryException.class);

        verify(inventoryReservationRepository, never()).insertAll(any());
    }

    @Test
    @DisplayName("reserve: 동일 checkout+lines ACTIVE면 재사용한다")
    void reserve_idempotentReuse() {
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(10));
        InventoryReservation existing = InventoryReservation.reconstitute(
                new InventoryReservationId(RESERVATION_ID),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(VARIANT_ID),
                1,
                InventoryReservationStatus.ACTIVE,
                expiresAt,
                Instant.now(),
                Instant.now()
        );
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of(existing));

        ReserveInventoryResult result = service.reserve(command(1));

        assertThat(result.reusedExisting()).isTrue();
        assertThat(result.lines()).hasSize(1);
        verify(inventoryReservationRepository, never()).insertAll(any());
        verify(inventoryVariantStockPort, never()).touchVersions(any());
    }

    @Test
    @DisplayName("reserve: ACTIVE lines가 다르면 release 후 새로 예약한다")
    void reserve_differentLines_releasesThenCreates() {
        Instant now = Instant.now();
        UUID otherVariant = UUID.randomUUID();
        InventoryReservation existing = InventoryReservation.reconstitute(
                new InventoryReservationId(RESERVATION_ID),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(otherVariant),
                1,
                InventoryReservationStatus.ACTIVE,
                now.plus(Duration.ofMinutes(10)),
                now,
                now
        );
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of(existing));
        when(inventoryVariantStockPort.findOrderedByIds(any()))
                .thenReturn(List.of(new VariantStockSnapshot(
                        new ProductVariantId(VARIANT_ID), 5, true)));
        when(inventoryReservationRepository.sumActiveQuantityByVariantId(any(), any())).thenReturn(0);
        when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

        ReserveInventoryResult result = service.reserve(command(1));

        assertThat(result.reusedExisting()).isFalse();
        assertThat(existing.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
        InOrder inOrder = inOrder(inventoryReservationRepository, inventoryVariantStockPort);
        inOrder.verify(inventoryReservationRepository).update(existing);
        inOrder.verify(inventoryReservationRepository).flush();
        inOrder.verify(inventoryVariantStockPort).touchVersions(any());
        inOrder.verify(inventoryReservationRepository).insertAll(any());
    }

    @Test
    @DisplayName("reserve: RELEASED만 있으면 재예약을 허용한다")
    void reserve_afterReleased_allowsNew() {
        Instant now = Instant.now();
        InventoryReservation released = InventoryReservation.reconstitute(
                new InventoryReservationId(RESERVATION_ID),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(VARIANT_ID),
                1,
                InventoryReservationStatus.RELEASED,
                now.minus(Duration.ofMinutes(1)),
                now.minus(Duration.ofMinutes(20)),
                now.minus(Duration.ofMinutes(1))
        );
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of(released));
        when(inventoryVariantStockPort.findOrderedByIds(any()))
                .thenReturn(List.of(new VariantStockSnapshot(
                        new ProductVariantId(VARIANT_ID), 5, true)));
        when(inventoryReservationRepository.sumActiveQuantityByVariantId(any(), any())).thenReturn(0);
        when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

        ReserveInventoryResult result = service.reserve(command(1));

        assertThat(result.reusedExisting()).isFalse();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<InventoryReservation>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(inventoryReservationRepository).insertAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("reserve: ACTIVE unique 제약 위반이면 재시도 후 성공한다")
    void reserve_activeUniqueConflict_retries() {
        when(inventoryReservationRepository.findByCheckoutId(any()))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(inventoryVariantStockPort.findOrderedByIds(any()))
                .thenReturn(List.of(new VariantStockSnapshot(
                        new ProductVariantId(VARIANT_ID), 5, true)));
        when(inventoryReservationRepository.sumActiveQuantityByVariantId(any(), any())).thenReturn(0);
        when(idGenerator.generateId()).thenReturn(RESERVATION_ID, UUID.randomUUID());

        DataIntegrityViolationException uniqueConflict = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \""
                        + InventoryReservationApplicationService.ACTIVE_CHECKOUT_VARIANT_UNIQUE_INDEX
                        + "\""
        );
        doThrow(uniqueConflict)
                .doAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                })
                .when(txExecutor).executeInNewTransaction(any());

        ReserveInventoryResult result = service.reserve(command(1));

        assertThat(result.reusedExisting()).isFalse();
        verify(txExecutor, org.mockito.Mockito.times(2)).executeInNewTransaction(any());
    }

    @Test
    @DisplayName("reserve: OptimisticLockingFailureException이면 재시도 후 성공한다")
    void reserve_optimisticLockConflict_retries() {
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of());
        when(inventoryVariantStockPort.findOrderedByIds(any()))
                .thenReturn(List.of(new VariantStockSnapshot(
                        new ProductVariantId(VARIANT_ID), 5, true)));
        when(inventoryReservationRepository.sumActiveQuantityByVariantId(any(), any())).thenReturn(0);
        when(idGenerator.generateId()).thenReturn(RESERVATION_ID);
        doThrow(new OptimisticLockingFailureException("version conflict"))
                .doAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                })
                .when(txExecutor).executeInNewTransaction(any());

        ReserveInventoryResult result = service.reserve(command(1));

        assertThat(result.reusedExisting()).isFalse();
        verify(txExecutor, org.mockito.Mockito.times(2)).executeInNewTransaction(any());
    }

    @Test
    @DisplayName("reserve: CONFIRMED 예약이 있으면 예외")
    void reserve_confirmedExisting_throws() {
        Instant now = Instant.now();
        InventoryReservation confirmed = InventoryReservation.reconstitute(
                new InventoryReservationId(RESERVATION_ID),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(VARIANT_ID),
                1,
                InventoryReservationStatus.CONFIRMED,
                now.plus(Duration.ofMinutes(10)),
                now,
                now
        );
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of(confirmed));

        assertThatThrownBy(() -> service.reserve(command(1)))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    @DisplayName("reserve: variant를 찾을 수 없으면 예외")
    void reserve_variantNotFound_throws() {
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of());
        when(inventoryVariantStockPort.findOrderedByIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.reserve(command(1)))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("not found");
        verify(inventoryReservationRepository, never()).insertAll(any());
    }

    @Test
    @DisplayName("reserve: variant가 reservable=false면 예외")
    void reserve_notReservable_throws() {
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of());
        when(inventoryVariantStockPort.findOrderedByIds(any()))
                .thenReturn(List.of(new VariantStockSnapshot(
                        new ProductVariantId(VARIANT_ID), 5, false)));

        assertThatThrownBy(() -> service.reserve(command(1)))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("not reservable");
        verify(inventoryReservationRepository, never()).insertAll(any());
    }

    @Test
    @DisplayName("reserve: lines가 비어 있으면 예외")
    void reserve_emptyLines_throws() {
        assertThatThrownBy(() -> service.reserve(new ReserveInventoryCommand(CHECKOUT_ID, List.of())))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("at least one line");
    }

    @Test
    @DisplayName("reserve: 중복 productVariantId면 예외")
    void reserve_duplicateVariant_throws() {
        ReserveInventoryCommand command = new ReserveInventoryCommand(
                CHECKOUT_ID,
                List.of(
                        new ReserveInventoryCommand.ReserveInventoryLine(VARIANT_ID, 1),
                        new ReserveInventoryCommand.ReserveInventoryLine(VARIANT_ID, 2)
                )
        );

        assertThatThrownBy(() -> service.reserve(command))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Duplicate productVariantId");
    }

    @Test
    @DisplayName("reserve: 다른 DataIntegrityViolationException은 재시도하지 않는다")
    void reserve_otherIntegrityViolation_doesNotRetry() {
        DataIntegrityViolationException other = new DataIntegrityViolationException(
                "ERROR: null value in column \"quantity\" violates not-null constraint"
        );
        doThrow(other).when(txExecutor).executeInNewTransaction(any());

        assertThatThrownBy(() -> service.reserve(command(1)))
                .isSameAs(other);
        verify(txExecutor, org.mockito.Mockito.times(1)).executeInNewTransaction(any());
    }

    @Test
    @DisplayName("isActiveCheckoutVariantUniqueViolation: 인덱스명이 cause 체인에 있으면 true")
    void isActiveCheckoutVariantUniqueViolation_detectsIndexName() {
        DataIntegrityViolationException nested = new DataIntegrityViolationException(
                "outer",
                new RuntimeException(
                        "detail: Key already exists ("
                                + InventoryReservationApplicationService.ACTIVE_CHECKOUT_VARIANT_UNIQUE_INDEX
                                + ")"
                )
        );
        assertThat(InventoryReservationApplicationService.isActiveCheckoutVariantUniqueViolation(nested))
                .isTrue();
        assertThat(InventoryReservationApplicationService.isActiveCheckoutVariantUniqueViolation(
                new DataIntegrityViolationException("fk_other_constraint"))).isFalse();
    }

    @Test
    @DisplayName("confirm: CONFIRMED만 있으면 idempotent하게 종료한다")
    void confirm_allConfirmed_isIdempotent() {
        Instant now = Instant.now();
        InventoryReservation confirmed = InventoryReservation.reconstitute(
                new InventoryReservationId(RESERVATION_ID),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(VARIANT_ID),
                1,
                InventoryReservationStatus.CONFIRMED,
                now.plus(Duration.ofMinutes(10)),
                now,
                now
        );
        InventoryReservation released = InventoryReservation.reconstitute(
                new InventoryReservationId(UUID.randomUUID()),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(UUID.randomUUID()),
                1,
                InventoryReservationStatus.RELEASED,
                now.minus(Duration.ofMinutes(1)),
                now.minus(Duration.ofMinutes(20)),
                now.minus(Duration.ofMinutes(1))
        );
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of(confirmed, released));

        service.confirm(CHECKOUT_ID);

        verify(inventoryVariantStockPort, never()).decreaseOnHandForConfirmedHold(any(), any(Integer.class));
        verify(inventoryReservationRepository, never()).update(any());
    }

    @Test
    @DisplayName("confirm: 만료된 ACTIVE hold는 확정할 수 없다")
    void confirm_expiredActive_throws() {
        Instant now = Instant.now();
        InventoryReservation expired = InventoryReservation.reconstitute(
                new InventoryReservationId(RESERVATION_ID),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(VARIANT_ID),
                1,
                InventoryReservationStatus.ACTIVE,
                now.minusSeconds(1),
                now.minus(Duration.ofMinutes(20)),
                now.minus(Duration.ofMinutes(20))
        );
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of(expired));

        assertThatThrownBy(() -> service.confirm(CHECKOUT_ID))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("expired");
        verify(inventoryVariantStockPort, never()).decreaseOnHandForConfirmedHold(any(), any(Integer.class));
    }

    @Test
    @DisplayName("confirm: 예약이 없으면 not found 예외")
    void confirm_noReservations_throws() {
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.confirm(CHECKOUT_ID))
                .isInstanceOf(InventoryReservationNotFoundException.class);
    }

    @Test
    @DisplayName("confirm: ACTIVE hold를 확정하고 on-hand를 차감한다")
    void confirm_decreasesOnHand() {
        Instant now = Instant.now();
        InventoryReservation existing = InventoryReservation.reconstitute(
                new InventoryReservationId(RESERVATION_ID),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(VARIANT_ID),
                2,
                InventoryReservationStatus.ACTIVE,
                now.plus(Duration.ofMinutes(10)),
                now,
                now
        );
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of(existing));

        service.confirm(CHECKOUT_ID);

        verify(inventoryVariantStockPort, never()).touchVersions(any());
        verify(inventoryVariantStockPort).decreaseOnHandForConfirmedHold(
                eq(new ProductVariantId(VARIANT_ID)), eq(2));
        verify(inventoryReservationRepository).update(existing);
        assertThat(existing.getStatus()).isEqualTo(InventoryReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("confirm: ACTIVE와 CONFIRMED가 섞이면 예외")
    void confirm_mixedState_throws() {
        Instant now = Instant.now();
        InventoryReservation active = InventoryReservation.reconstitute(
                new InventoryReservationId(UUID.randomUUID()),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(VARIANT_ID),
                1,
                InventoryReservationStatus.ACTIVE,
                now.plus(Duration.ofMinutes(10)),
                now,
                now
        );
        InventoryReservation confirmed = InventoryReservation.reconstitute(
                new InventoryReservationId(UUID.randomUUID()),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(UUID.randomUUID()),
                1,
                InventoryReservationStatus.CONFIRMED,
                now.plus(Duration.ofMinutes(10)),
                now,
                now
        );
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of(active, confirmed));

        assertThatThrownBy(() -> service.confirm(CHECKOUT_ID))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("mixed ACTIVE and CONFIRMED");
    }

    @Test
    @DisplayName("release: ACTIVE가 없으면 no-op한다")
    void release_noActive_isNoOp() {
        Instant now = Instant.now();
        InventoryReservation released = InventoryReservation.reconstitute(
                new InventoryReservationId(RESERVATION_ID),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(VARIANT_ID),
                1,
                InventoryReservationStatus.RELEASED,
                now.minus(Duration.ofMinutes(1)),
                now.minus(Duration.ofMinutes(20)),
                now.minus(Duration.ofMinutes(1))
        );
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of(released));

        service.release(CHECKOUT_ID);

        verify(inventoryReservationRepository, never()).update(any());
    }

    @Test
    @DisplayName("release: 예약이 없으면 not found 예외")
    void release_noReservations_throws() {
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.release(CHECKOUT_ID))
                .isInstanceOf(InventoryReservationNotFoundException.class);
    }

    @Test
    @DisplayName("release: ACTIVE hold를 RELEASED로 전이한다")
    void release_activeHold() {
        Instant now = Instant.now();
        InventoryReservation existing = InventoryReservation.reconstitute(
                new InventoryReservationId(RESERVATION_ID),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(VARIANT_ID),
                1,
                InventoryReservationStatus.ACTIVE,
                now.plus(Duration.ofMinutes(10)),
                now,
                now
        );
        when(inventoryReservationRepository.findByCheckoutId(any())).thenReturn(List.of(existing));

        service.release(CHECKOUT_ID);

        verify(inventoryReservationRepository).update(existing);
        assertThat(existing.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
        verify(inventoryVariantStockPort, never()).decreaseOnHandForConfirmedHold(any(), any(Integer.class));
    }

    @Test
    @DisplayName("expireDueReservations: 만료 대상 ACTIVE hold를 EXPIRED로 전이한다")
    void expireDueReservations_expiresDueRows() {
        Instant now = Instant.now();
        InventoryReservation due = InventoryReservation.reconstitute(
                new InventoryReservationId(RESERVATION_ID),
                new CheckoutId(CHECKOUT_ID),
                new ProductVariantId(VARIANT_ID),
                2,
                InventoryReservationStatus.ACTIVE,
                now.minusSeconds(30),
                now.minus(Duration.ofMinutes(20)),
                now.minus(Duration.ofMinutes(20))
        );
        when(inventoryReservationRepository.findDueActiveForUpdate(any(), eq(25)))
                .thenReturn(List.of(due));

        int expired = service.expireDueReservations(25);

        assertThat(expired).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(InventoryReservationStatus.EXPIRED);
        verify(inventoryReservationRepository).update(due);
    }

    @Test
    @DisplayName("expireDueReservations: batchSize가 0 이하면 properties 기본값을 사용한다")
    void expireDueReservations_usesDefaultBatchSizeWhenNonPositive() {
        properties.setExpireBatchSize(42);
        when(inventoryReservationRepository.findDueActiveForUpdate(any(), eq(42)))
                .thenReturn(List.of());

        assertThat(service.expireDueReservations(0)).isZero();
        verify(inventoryReservationRepository).findDueActiveForUpdate(any(), eq(42));
    }

    private static ReserveInventoryCommand command(int quantity) {
        return new ReserveInventoryCommand(
                CHECKOUT_ID,
                List.of(new ReserveInventoryCommand.ReserveInventoryLine(VARIANT_ID, quantity))
        );
    }
}
