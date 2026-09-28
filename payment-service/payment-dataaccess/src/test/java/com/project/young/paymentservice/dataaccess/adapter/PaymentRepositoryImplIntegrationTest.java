package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.dto.command.ObserveProviderRefundCommand;
import com.project.young.paymentservice.application.dto.command.RecordCustomerRefundDltCommand;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort.Kind;
import com.project.young.paymentservice.dataaccess.repository.CustomerRefundDltJpaRepository;
import com.project.young.paymentservice.dataaccess.repository.ProviderRefundWebhookInboxJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.dataaccess.config.PaymentDataAccessConfig;
import com.project.young.paymentservice.dataaccess.mapper.PaymentAggregateMapper;
import com.project.young.paymentservice.dataaccess.mapper.PaymentDataAccessMapper;
import com.project.young.paymentservice.dataaccess.repository.CustomerRefundProcessingJpaRepository;
import com.project.young.paymentservice.dataaccess.repository.PaymentJpaRepository;
import com.project.young.paymentservice.dataaccess.repository.PaymentRefundClaimJpaRepository;
import com.project.young.paymentservice.dataaccess.repository.PaymentOutboxJpaRepository;
import com.project.young.paymentservice.dataaccess.repository.CustomerRefundReviewJpaRepository;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;
import com.project.young.paymentservice.domain.valueobject.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = PaymentRepositoryImplIntegrationTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentRepositoryImplIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String jdbcUrl = postgresContainer.getJdbcUrl();
            return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=payments";
        });
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "payments");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "payments");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private PaymentRepositoryImpl paymentRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private CustomerRefundProcessingJpaRepository customerRefundProcessingJpaRepository;

    @Autowired
    private PaymentRefundClaimJpaRepository paymentRefundClaimJpaRepository;

    @Autowired
    private PaymentOutboxJpaRepository paymentOutboxJpaRepository;

    @Autowired
    private CustomerRefundReviewJpaRepository customerRefundReviewJpaRepository;

    @Autowired
    private CustomerRefundDltJpaRepository customerRefundDltJpaRepository;

    @Autowired
    private ProviderRefundWebhookInboxJpaRepository refundInboxRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("TRUNCATE TABLE payments.payment_provider_events, payments.payment_outbox, payments.payments CASCADE")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("insert/findByOrderId: 결제를 저장하고 orderId로 조회한다")
    void insertAndFindByOrderId() {
        PaymentId paymentId = new PaymentId(UUID.randomUUID());
        OrderId orderId = new OrderId(UUID.randomUUID());
        Payment payment = Payment.createPending(
                paymentId,
                orderId,
                new UserId("user-1"),
                new Money(new BigDecimal("25.00"))
        );

        paymentRepository.insert(payment);

        Payment loaded = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(paymentId);
        assertThat(loaded.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(loaded.getCurrency()).isEqualTo("USD");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void customerRefundRepository_isRegisteredAndV14MigrationIsApplied() {
        assertThat(entityManager.createNativeQuery("SELECT current_schema()")
                .getSingleResult()).isEqualTo("payments");
        assertThat(customerRefundProcessingJpaRepository.existsByRefundId(UUID.randomUUID())).isFalse();
        assertThat(paymentRefundClaimJpaRepository.findById(UUID.randomUUID())).isEmpty();
        Number claimCount = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM payments.payment_refund_claims")
                .getSingleResult();
        assertThat(claimCount.longValue()).isZero();
    }

    @Test
    void paymentRefundClaimRepository_insertsOnceAndMarksFirstAttempt() {
        UUID paymentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        paymentRepository.insert(Payment.createPending(
                new PaymentId(paymentId), new OrderId(UUID.randomUUID()),
                new UserId("user-1"), new Money(new BigDecimal("25.00"))));
        entityManager.flush();

        assertThat(paymentRefundClaimJpaRepository.insertIfAbsent(paymentId, requestId, "CUSTOMER")).isEqualTo(1);
        assertThat(paymentRefundClaimJpaRepository.insertIfAbsent(paymentId, UUID.randomUUID(), "COMPENSATION"))
                .isZero();
        Instant firstAttemptAt = Instant.parse("2026-09-27T00:00:00Z");
        assertThat(paymentRefundClaimJpaRepository.markAttemptStarted(paymentId, firstAttemptAt)).isEqualTo(1);
        assertThat(paymentRefundClaimJpaRepository.markAttemptStarted(paymentId, firstAttemptAt.plusSeconds(60)))
                .isZero();
        assertThat(paymentRefundClaimJpaRepository.findById(paymentId))
                .hasValueSatisfying(claim -> {
                    assertThat(claim.getRequestId()).isEqualTo(requestId);
                    assertThat(claim.getRequestKind()).isEqualTo(Kind.CUSTOMER);
                    assertThat(claim.getFirstAttemptAt()).isEqualTo(firstAttemptAt);
                });
    }

    @Test
    void pendingRefundClaim_isReconciledUntilCompletedLedgerExists() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        paymentRepository.insert(Payment.createPending(new PaymentId(paymentId), new OrderId(orderId),
                new UserId("user-1"), new Money(new BigDecimal("25.00"))));
        entityManager.flush();
        paymentRefundClaimJpaRepository.insertIfAbsent(paymentId, refundId, "CUSTOMER");
        paymentRefundClaimJpaRepository.markAttemptStarted(paymentId, Instant.now());
        paymentRefundClaimJpaRepository.recordProviderResult(paymentId, refundId, "CUSTOMER",
                "re_pending", "PENDING", Instant.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(paymentRefundClaimJpaRepository.findUnfinalized(PageRequest.of(0, 100)))
                .extracting(claim -> claim.getPaymentId()).contains(paymentId);

        paymentRefundClaimJpaRepository.recordProviderResult(paymentId, refundId, "CUSTOMER",
                "re_pending", "SUCCEEDED", Instant.now());
        customerRefundProcessingJpaRepository.insert(refundId, paymentId, orderId, "user-1", Instant.now());
        entityManager.flush();
        entityManager.clear();
        assertThat(paymentRefundClaimJpaRepository.findUnfinalized(PageRequest.of(0, 100))).isEmpty();
    }

    @Test
    void failedRefundOutbox_isInsertedOnlyOnce() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        paymentRepository.insert(Payment.createPending(new PaymentId(paymentId), new OrderId(orderId),
                new UserId("user-1"), new Money(new BigDecimal("25.00"))));
        entityManager.flush();

        assertThat(paymentOutboxJpaRepository.insertCustomerRefundFailed(UUID.randomUUID(), refundId,
                paymentId, orderId, "user-1", "PSP refund failed", Instant.now(),
                2, false, null, Instant.now())).isEqualTo(1);
        assertThat(paymentOutboxJpaRepository.insertCustomerRefundFailed(UUID.randomUUID(), refundId,
                paymentId, orderId, "user-1", "PSP refund failed", Instant.now(),
                2, false, null, Instant.now())).isZero();
    }

    @Test
    void customerReview_isIdempotentAndExcludesOnlyCustomerClaimFromReconciliation() {
        UUID paymentId = UUID.randomUUID();
        UUID compensationPaymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        Instant now = Instant.now();
        for (UUID id : java.util.List.of(paymentId, compensationPaymentId)) {
            paymentRepository.insert(Payment.createPending(new PaymentId(id), new OrderId(UUID.randomUUID()),
                    new UserId("user-1"), new Money(new BigDecimal("25.00"))));
        }
        entityManager.flush();
        paymentRefundClaimJpaRepository.insertIfAbsent(paymentId, refundId, "CUSTOMER");
        paymentRefundClaimJpaRepository.markAttemptStarted(paymentId, now);
        paymentRefundClaimJpaRepository.recordProviderResult(paymentId, refundId, "CUSTOMER", "re_pending", "PENDING", now);
        paymentRefundClaimJpaRepository.insertIfAbsent(compensationPaymentId, refundId, "COMPENSATION");
        paymentRefundClaimJpaRepository.markAttemptStarted(compensationPaymentId, now);
        assertThat(paymentRefundClaimJpaRepository.findUnfinalized(PageRequest.of(0, 100))).hasSize(2);

        assertThat(customerRefundReviewJpaRepository.insertIfAbsent(refundId, paymentId, "re_pending",
                "NeedsReview", "Unknown outcome", now)).isEqualTo(1);
        assertThat(customerRefundReviewJpaRepository.insertIfAbsent(refundId, paymentId, "re_pending",
                "NeedsReview", "Repeated observation", now)).isZero();
        entityManager.clear();

        assertThat(paymentRefundClaimJpaRepository.isCustomerReviewEscalated(paymentId, refundId)).isTrue();
        assertThat(paymentRefundClaimJpaRepository.findUnfinalized(PageRequest.of(0, 100)))
                .extracting(claim -> claim.getPaymentId()).containsExactly(compensationPaymentId);
        assertThat(customerRefundReviewJpaRepository.findById(refundId)).hasValueSatisfying(review -> {
            assertThat(review.getHandlingStatus().name()).isEqualTo("ESCALATED");
            assertThat(review.getFailureMessage()).isEqualTo("Unknown outcome");
        });
        assertThat(paymentRefundClaimJpaRepository.findById(paymentId)).hasValueSatisfying(claim ->
                assertThat(claim.getProviderRefundState()).isEqualTo("PENDING"));
        assertThat(customerRefundProcessingJpaRepository.existsByRefundId(refundId)).isFalse();
        assertThat(paymentOutboxJpaRepository.count()).isZero();
    }

    @Test
    @DisplayName("updateStatus: PENDING에서 COMPLETED로 CAS 업데이트한다")
    void updateStatus_transitionsPendingToCompleted() {
        PaymentId paymentId = new PaymentId(UUID.randomUUID());
        OrderId orderId = new OrderId(UUID.randomUUID());
        Payment payment = Payment.createPending(
                paymentId,
                orderId,
                new UserId("user-1"),
                new Money(new BigDecimal("10.00"))
        );
        paymentRepository.insert(payment);

        payment.complete();
        boolean updated = paymentRepository.updateStatus(payment, PaymentStatus.PENDING);

        assertThat(updated).isTrue();
        Payment loaded = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(paymentJpaRepository.findById(paymentId.getValue()).orElseThrow().getUpdatedAt())
                .isAfterOrEqualTo(loaded.getCreatedAt());
    }

    @Configuration
    @Import({
            PaymentDataAccessConfig.class,
            PaymentRepositoryImpl.class,
            PaymentDataAccessMapper.class,
            PaymentAggregateMapper.class
    })
    static class Config {
    }

    @Test
    void customerObservation_failedIsAbsorbingAndRetainsSuccessfulHistory() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        paymentRepository.insert(Payment.createPending(new PaymentId(paymentId), new OrderId(UUID.randomUUID()),
                new UserId("user-1"), new Money(new BigDecimal("25.00"))));
        entityManager.flush();
        paymentRefundClaimJpaRepository.insertIfAbsent(paymentId, refundId, "CUSTOMER");
        Instant completedAt = Instant.parse("2026-09-28T00:00:00Z");
        Instant failedAt = completedAt.plusSeconds(60);
        assertThat(paymentRefundClaimJpaRepository.recordCustomerObservation(paymentId, refundId,
                "re_correction", "SUCCEEDED", completedAt)).isEqualTo(1);
        assertThat(paymentRefundClaimJpaRepository.recordCustomerObservation(paymentId, refundId,
                "re_correction", "FAILED", failedAt)).isEqualTo(1);
        assertThat(paymentRefundClaimJpaRepository.recordCustomerObservation(paymentId, refundId,
                "re_correction", "SUCCEEDED", failedAt.plusSeconds(60))).isZero();
        assertThat(paymentRefundClaimJpaRepository.recordCustomerObservation(paymentId, refundId,
                "re_correction", "PENDING", failedAt.plusSeconds(60))).isZero();
        assertThat(paymentRefundClaimJpaRepository.findByProviderRefundId("re_correction")).hasValueSatisfying(claim -> {
            assertThat(claim.getProviderRefundState()).isEqualTo("FAILED");
            assertThat(claim.getProviderRefundSucceededAt()).isEqualTo(completedAt);
            assertThat(claim.getProviderRefundFailedAt()).isEqualTo(failedAt);
        });
    }

    @Test
    void customerDlt_malformedPayloadIsStoredOnceWithoutPaymentForeignKey() {
        var command = new RecordCustomerRefundDltCommand(
                "customer.refund.requested.DLT", 0, 42, "unknown", "not-json",
                "customer.refund.requested", 0, 17L, "InvalidPayload", "invalid");
        assertThat(customerRefundDltJpaRepository.insertIfAbsent(command)).isEqualTo(1);
        assertThat(customerRefundDltJpaRepository.insertIfAbsent(command)).isZero();
        assertThat(customerRefundDltJpaRepository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getPayload()).isEqualTo("not-json");
            assertThat(row.getHandlingStatus().name()).isEqualTo("ESCALATED");
            assertThat(row.getId()).isNotNull();
        });
    }

    @Test
    void refundInbox_reclaimsExpiredLeaseAndFencesOldWorker() {
        var command = new ObserveProviderRefundCommand(
                "evt_inbox", "re_inbox", "pi_inbox",
                RefundState.FAILED, "bank rejected");
        assertThat(refundInboxRepository.insertIfAbsent(command, command.failureReason())).isEqualTo(1);
        assertThat(refundInboxRepository.insertIfAbsent(command, command.failureReason())).isZero();
        Instant now = Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS);
        assertThat(refundInboxRepository.claim(command.eventId(), now, now.minusSeconds(300))).isEqualTo(1);
        assertThat(refundInboxRepository.claim(command.eventId(), now, now.minusSeconds(300))).isZero();
        Instant reclaimedAt = now.plusSeconds(600);
        assertThat(refundInboxRepository.claim(command.eventId(), reclaimedAt, now.plusSeconds(300))).isEqualTo(1);
        assertThat(refundInboxRepository.markApplied(command.eventId(), now)).isZero();
        assertThat(refundInboxRepository.retryOrEscalate(command.eventId(), reclaimedAt, 2,
                reclaimedAt.plusSeconds(30), "UnmatchedRefundClaim")).isEqualTo(1);
        entityManager.clear();
        assertThat(refundInboxRepository.findById(command.eventId())).hasValueSatisfying(row -> {
            assertThat(row.getStatus().name()).isEqualTo("ESCALATED");
            assertThat(row.getAttempts()).isEqualTo(2);
        });
    }

    @Test
    void confirmedLateFailure_retainsOriginalUncertainOutcomeDiagnostics() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        paymentRepository.insert(Payment.createPending(new PaymentId(paymentId), new OrderId(UUID.randomUUID()),
                new UserId("user-1"), new Money(new BigDecimal("25.00"))));
        entityManager.flush();
        customerRefundReviewJpaRepository.insertIfAbsent(refundId, paymentId, "re_review",
                "UnknownOutcome", "Original diagnostic", Instant.now());
        customerRefundReviewJpaRepository.recordConfirmedLateFailure(refundId, paymentId, "re_review", "bank rejected");
        entityManager.clear();
        assertThat(customerRefundReviewJpaRepository.findById(refundId)).hasValueSatisfying(review -> {
            assertThat(review.getReviewReason().name()).isEqualTo("CONFIRMED_LATE_FAILURE");
            assertThat(review.getFailureExceptionClass()).isEqualTo("UnknownOutcome");
            assertThat(review.getFailureMessage()).isEqualTo("Original diagnostic");
            assertThat(review.getConfirmedFailureReason()).isEqualTo("bank rejected");
            assertThat(review.getConfirmedFailedAt()).isNotNull();
        });
    }
}
