package com.project.young.paymentservice.it;

import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundResult;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import com.project.young.paymentservice.application.service.PaymentRefundResultRecorder;
import com.project.young.paymentservice.dataaccess.adapter.CustomerRefundReviewAdapter;
import java.util.Objects;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.project.young.paymentservice.PaymentServiceMain;
import com.project.young.paymentservice.application.dto.command.RefundCustomerPaymentCommand;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import com.project.young.paymentservice.application.service.RefundCompensationDltReplayExecutor;
import com.project.young.paymentservice.it.support.PaymentIntegrationTestConfiguration;
import com.project.young.paymentservice.it.support.PaymentIntegrationTestConfiguration.RecordingPaymentProvider;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = PaymentServiceMain.class
)
@Import(PaymentIntegrationTestConfiguration.class)
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentOrderCreatedKafkaIntegrationTest {

    private static final String USER_ID = "018f0000-0000-7000-8000-000000000101";

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        String jdbcUrl = postgresContainer.getJdbcUrl() + "&currentSchema=payments";
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("kafka-config.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("payment-service.saga-events.enabled", () -> "true");
        registry.add("payment-service.saga-events.order-created-topic", () -> "order.created");
        registry.add("payment-service.saga-events.order-created-consumer-group", () -> "payment-it-order-created");
        registry.add("payment-service.saga-events.refund-requested-topic", () -> "payment.refund.requested");
        registry.add("payment-service.saga-events.refund-requested-consumer-group", () -> "payment-it-refund-requested");
        registry.add("payment-service.stub-payment.always-succeed", () -> "false");
        registry.add("payment-service.stub-payment.decline-when-fractional-part", () -> "0.99");
        registry.add("payment-service.saga-events.consumer.retry.max-attempts", () -> "1");
        registry.add("payment-service.saga-events.consumer.retry.backoff-interval-ms", () -> "10");
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RecordingPaymentProvider paymentProvider;

    @Autowired
    private RefundCompensationDltReplayExecutor refundDltReplayExecutor;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private PaymentRefundResultRecorder refundResultRecorder;

    @MockitoSpyBean
    private CustomerRefundReviewAdapter customerReviews;

    @BeforeEach
    void setUp() {
        paymentProvider.reset();
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                    TRUNCATE TABLE payments.payment_refund_compensation_dlts, payments.payment_refund_compensations, payments.payment_provider_events,
                    payments.customer_refund_dlts, payments.provider_refund_webhook_inbox,
                    payments.payment_outbox, payments.payments RESTART IDENTITY CASCADE
                    """)
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();
        });
    }

    @Test
    @DisplayName("order.created JSON을 소비하면 결제를 완료하고 payment_outbox에 PAYMENT_COMPLETED를 넣는다")
    void orderCreated_processesPaymentAndEnqueuesCompletedOutbox() {
        UUID orderId = UUID.randomUUID();
        sendOrderCreated(orderId, "50.00");

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Object[] row = transactionTemplate.execute(status -> {
                @SuppressWarnings("unchecked")
                var payments = entityManager.createNativeQuery(
                                "SELECT CAST(status AS varchar), CAST(amount AS varchar) FROM payments WHERE order_id = CAST(:orderId AS uuid)")
                        .setParameter("orderId", orderId)
                        .getResultList();
                @SuppressWarnings("unchecked")
                var outbox = entityManager.createNativeQuery(
                                "SELECT event_type, currency FROM payment_outbox WHERE order_id = CAST(:orderId AS uuid)")
                        .setParameter("orderId", orderId)
                        .getResultList();
                if (payments.isEmpty() || outbox.isEmpty()) {
                    return null;
                }
                Object[] payment = (Object[]) payments.getFirst();
                Object[] event = (Object[]) outbox.getFirst();
                return new Object[] {payment[0], payment[1], event[0], event[1]};
            });
            assertThat(row).isNotNull();
            assertThat(row[0]).isEqualTo("COMPLETED");
            assertThat(new BigDecimal(row[1].toString())).isEqualByComparingTo("50.00");
            assertThat(row[2]).isEqualTo("PAYMENT_COMPLETED");
            assertThat(row[3]).isEqualTo("USD");
        });
    }

    @Test
    @DisplayName("order.created 금액 소수부가 0.99이면 stub 거절 후 PAYMENT_FAILED outbox를 넣는다")
    void orderCreated_whenStubDeclines_enqueuesFailedOutbox() {
        UUID orderId = UUID.randomUUID();
        sendOrderCreated(orderId, "49.99");

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Object[] row = transactionTemplate.execute(status -> {
                @SuppressWarnings("unchecked")
                var payments = entityManager.createNativeQuery(
                                "SELECT CAST(status AS varchar), failure_reason FROM payments WHERE order_id = CAST(:orderId AS uuid)")
                        .setParameter("orderId", orderId)
                        .getResultList();
                @SuppressWarnings("unchecked")
                var outbox = entityManager.createNativeQuery(
                                "SELECT event_type, failure_reason FROM payment_outbox WHERE order_id = CAST(:orderId AS uuid)")
                        .setParameter("orderId", orderId)
                        .getResultList();
                if (payments.isEmpty() || outbox.isEmpty()) {
                    return null;
                }
                Object[] payment = (Object[]) payments.getFirst();
                Object[] event = (Object[]) outbox.getFirst();
                return new Object[] {payment[0], payment[1], event[0], event[1]};
            });
            assertThat(row).isNotNull();
            assertThat(row[0]).isEqualTo("FAILED");
            assertThat(row[1]).isNotNull();
            assertThat(row[2]).isEqualTo("PAYMENT_FAILED");
            assertThat(row[3]).isNotNull();
        });
    }

    @Test
    @DisplayName("payment.refund.requested JSON을 소비하면 보상 이벤트를 멱등하게 기록한다")
    void paymentRefundRequested_refundsAndRecordsCompensationOnce() {
        UUID orderId = UUID.randomUUID();
        sendOrderCreated(orderId, "50.00");

        UUID paymentId = await().atMost(20, TimeUnit.SECONDS).until(() -> transactionTemplate.execute(status -> {
            @SuppressWarnings("unchecked")
            var paymentIds = entityManager.createNativeQuery(
                            "SELECT id FROM payments WHERE order_id = CAST(:orderId AS uuid)")
                    .setParameter("orderId", orderId)
                    .getResultList();
            return paymentIds.isEmpty() ? null : UUID.fromString(paymentIds.getFirst().toString());
        }), Objects::nonNull);
        awaitCompletedPayment(paymentId);
        UUID compensationEventId = UUID.randomUUID();

        sendPaymentRefundRequested(compensationEventId, paymentId, orderId);
        sendPaymentRefundRequested(compensationEventId, paymentId, orderId);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Number count = transactionTemplate.execute(status -> (Number) entityManager.createNativeQuery("""
                            SELECT COUNT(*) FROM payment_refund_compensations
                            WHERE compensation_event_id = CAST(:compensationEventId AS uuid)
                            """)
                    .setParameter("compensationEventId", compensationEventId)
                    .getSingleResult());
            assertThat(count).isNotNull();
            assertThat(count.longValue()).isEqualTo(1L);
            assertThat(paymentProvider.refundIdempotencyKeys()).containsExactly(compensationEventId.toString());
            assertThat(paymentProvider.refundTransactionActive()).containsExactly(false);
        });
    }

    @Test
    void customerRefund_recordsResultAndOutboxAfterPspCallWithoutOpenTransaction() {
        UUID orderId = UUID.randomUUID();
        sendOrderCreated(orderId, "50.00");
        UUID paymentId = awaitPaymentId(orderId);
        awaitCompletedPayment(paymentId);
        UUID refundId = UUID.randomUUID();
        RefundCustomerPaymentCommand command = new RefundCustomerPaymentCommand(
                refundId, paymentId, orderId, USER_ID);

        assertThat(paymentApplicationService.refundCustomerPayment(command)).isTrue();
        assertThat(paymentApplicationService.refundCustomerPayment(command)).isFalse();
        assertThat(paymentProvider.refundIdempotencyKeys()).containsExactly(refundId.toString());
        assertThat(paymentProvider.refundTransactionActive()).containsExactly(false);

        Object[] counts = transactionTemplate.execute(status -> new Object[] {
                entityManager.createNativeQuery("SELECT COUNT(*) FROM customer_refund_processings WHERE refund_id = CAST(:refundId AS uuid)")
                        .setParameter("refundId", refundId).getSingleResult(),
                entityManager.createNativeQuery("SELECT COUNT(*) FROM payment_outbox WHERE refund_id = CAST(:refundId AS uuid) AND event_type = 'CUSTOMER_REFUND_COMPLETED'")
                        .setParameter("refundId", refundId).getSingleResult()
        });
        assertThat(counts).isNotNull();
        assertThat(((Number) counts[0]).longValue()).isEqualTo(1L);
        assertThat(((Number) counts[1]).longValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("PSP 환불 consumer 실패는 retry/DLT 후 운영 replay에서 같은 멱등 키로 종결된다")
    void paymentRefundRequested_whenPspFails_routesToDltAndOperationalReplayUsesSameIdempotencyKey() {
        UUID orderId = UUID.randomUUID();
        sendOrderCreated(orderId, "50.00");
        UUID paymentId = awaitPaymentId(orderId);
        awaitCompletedPayment(paymentId);
        UUID compensationEventId = UUID.randomUUID();
        paymentProvider.failNextRefunds(2);

        sendPaymentRefundRequested(compensationEventId, paymentId, orderId);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Object[] dlt = transactionTemplate.execute(status -> findRefundDlt(compensationEventId));
            assertThat(dlt).isNotNull();
            assertThat(dlt[0]).isEqualTo("MANUAL");
        });

        refundDltReplayExecutor.replayManualItems();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Object[] replayed = transactionTemplate.execute(status -> findRefundDlt(compensationEventId));
            assertThat(replayed).isNotNull();
            assertThat(replayed[0]).isEqualTo("RESOLVED");
            assertThat(((Number) replayed[1]).intValue()).isEqualTo(1);
            assertThat(paymentProvider.refundIdempotencyKeys()).containsOnly(compensationEventId.toString());
        });
    }

    private Object[] findRefundDlt(UUID compensationEventId) {
        @SuppressWarnings("unchecked")
        var rows = entityManager.createNativeQuery("""
                        SELECT CAST(handling_status AS varchar), replay_attempts
                        FROM payment_refund_compensation_dlts
                        WHERE compensation_event_id = CAST(:compensationEventId AS uuid)
                        """)
                .setParameter("compensationEventId", compensationEventId)
                .getResultList();
        return rows.isEmpty() ? null : (Object[]) rows.getFirst();
    }

    private UUID awaitPaymentId(UUID orderId) {
        return await().atMost(20, TimeUnit.SECONDS).until(() -> transactionTemplate.execute(status -> {
            @SuppressWarnings("unchecked")
            var paymentIds = entityManager.createNativeQuery(
                            "SELECT id FROM payments WHERE order_id = CAST(:orderId AS uuid)")
                    .setParameter("orderId", orderId)
                    .getResultList();
            return paymentIds.isEmpty() ? null : UUID.fromString(paymentIds.getFirst().toString());
        }), Objects::nonNull);
    }

    private void awaitCompletedPayment(UUID paymentId) {
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String status = transactionTemplate.execute(transactionStatus -> (String) entityManager.createNativeQuery(
                            "SELECT CAST(status AS varchar) FROM payments WHERE id = CAST(:paymentId AS uuid)")
                    .setParameter("paymentId", paymentId)
                    .getSingleResult());
            assertThat(status).isEqualTo("COMPLETED");
        });
    }

    private static void sendOrderCreated(UUID orderId, String totalAmount) {
        UUID eventId = UUID.randomUUID();
        String json = """
                {
                  "id": "%s",
                  "event_id": "%s",
                  "order_id": "%s",
                  "user_id": "%s",
                  "total_amount": "%s",
                  "currency": "USD",
                  "occurred_at": "2026-07-16T00:00:00Z",
                  "published_at": null,
                  "created_at": "2026-07-16T00:00:00Z"
                }
                """.formatted(eventId, eventId, orderId, USER_ID, totalAmount);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("order.created", orderId.toString(), json))
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish order.created", ex);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "not-json"})
    void invalidCustomerRefundRequest_goesThroughDltToDurableQueueBeforeClaimExists(String payload) throws Exception {
        String key = UUID.randomUUID().toString();
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("customer.refund.requested", key, payload))
                    .get(10, TimeUnit.SECONDS);
        }
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Object[] row = transactionTemplate.execute(status -> {
                var rows = entityManager.createNativeQuery("""
                        SELECT source_topic, handling_status, payload FROM customer_refund_dlts WHERE message_key = :key
                        """).setParameter("key", key).getResultList();
                return rows.isEmpty() ? null : (Object[]) rows.getFirst();
            });
            assertThat(row).isNotNull();
            assertThat(row[0]).isEqualTo("customer.refund.requested");
            assertThat(row[1]).isEqualTo("ESCALATED");
            if ("not-json".equals(payload)) {
                assertThat(row[2]).isEqualTo(payload);
            }
        });
        Long claimCount = transactionTemplate.execute(status -> ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM payment_refund_claims").getSingleResult()).longValue());
        assertThat(claimCount).isZero();
    }

    @Test
    void lateFailureCorrection_isAtomicIdempotentAndDoesNotRepeatPspRefund() {
        UUID orderId = UUID.randomUUID();
        sendOrderCreated(orderId, "50.00");
        UUID paymentId = awaitPaymentId(orderId);
        awaitCompletedPayment(paymentId);
        UUID refundId = UUID.randomUUID();
        var command = new RefundCustomerPaymentCommand(refundId, paymentId, orderId, USER_ID);
        assertThat(paymentApplicationService.refundCustomerPayment(command)).isTrue();
        String providerRefundId = transactionTemplate.execute(status -> (String) entityManager.createNativeQuery(
                "SELECT provider_refund_id FROM payment_refund_claims WHERE payment_id = :id")
                .setParameter("id", paymentId).getSingleResult());
        var failed = new RefundResult(
                providerRefundId, RefundState.FAILED);
        Mockito.doThrow(new IllegalStateException("review database unavailable")).when(customerReviews)
                .recordConfirmedLateFailure(ArgumentMatchers.any(), ArgumentMatchers.any(),
                        ArgumentMatchers.any(), ArgumentMatchers.any());
        Assertions.assertThatThrownBy(() ->
                refundResultRecorder.recordCustomerObservation(command, failed, "bank rejected"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("review database unavailable");
        String providerState = transactionTemplate.execute(status -> (String) entityManager.createNativeQuery(
                "SELECT provider_refund_state FROM payment_refund_claims WHERE payment_id = :id")
                .setParameter("id", paymentId).getSingleResult());
        assertThat(providerState).isEqualTo("SUCCEEDED");
        assertThat(failureOutboxCount(refundId)).isZero();
        Mockito.doCallRealMethod().when(customerReviews).recordConfirmedLateFailure(
                ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any());

        refundResultRecorder.recordCustomerObservation(command, failed, "bank rejected");
        refundResultRecorder.recordCustomerObservation(command, failed, "bank rejected");
        refundResultRecorder.recordCustomerObservation(command,
                new RefundResult(providerRefundId,
                        RefundState.SUCCEEDED), null);
        assertThat(failureOutboxCount(refundId)).isEqualTo(1);
        Object[] state = transactionTemplate.execute(status -> (Object[]) entityManager.createNativeQuery("""
                SELECT c.provider_refund_state, o.failed_after_completion, o.result_version, r.review_reason,
                    (SELECT COUNT(*) FROM customer_refund_processings p WHERE p.refund_id = :refundId)
                FROM payment_refund_claims c JOIN payment_outbox o ON o.refund_id = c.request_id
                JOIN customer_refund_reviews r ON r.refund_id = c.request_id
                WHERE c.request_id = :refundId AND o.event_type = 'CUSTOMER_REFUND_FAILED'
                """).setParameter("refundId", refundId).getSingleResult());
        assertThat(state[0]).isEqualTo("FAILED");
        assertThat(state[1]).isEqualTo(true);
        assertThat(((Number) state[2]).longValue()).isEqualTo(2);
        assertThat(state[3]).isEqualTo("CONFIRMED_LATE_FAILURE");
        assertThat(((Number) state[4]).longValue()).isEqualTo(1);
        assertThat(paymentProvider.refundIdempotencyKeys()).containsExactly(refundId.toString());
    }

    private long failureOutboxCount(UUID refundId) {
        return transactionTemplate.execute(status -> ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM payment_outbox WHERE refund_id = :refundId AND event_type = 'CUSTOMER_REFUND_FAILED'
                """).setParameter("refundId", refundId).getSingleResult()).longValue());
    }

    private static void sendPaymentRefundRequested(UUID compensationEventId, UUID paymentId, UUID orderId) {
        String json = """
                {
                  "id": "%s",
                  "compensation_event_id": "%s",
                  "payment_id": "%s",
                  "order_id": "%s",
                  "user_id": "%s",
                  "reason": "inventory_unavailable",
                  "occurred_at": "2026-09-07T00:00:00Z",
                  "created_at": "2026-09-07T00:00:00Z"
                }
                """.formatted(UUID.randomUUID(), compensationEventId, paymentId, orderId, USER_ID);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("payment.refund.requested", paymentId.toString(), json))
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish payment.refund.requested", ex);
        }
    }
}
