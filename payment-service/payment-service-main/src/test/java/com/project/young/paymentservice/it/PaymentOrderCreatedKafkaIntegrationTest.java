package com.project.young.paymentservice.it;

import com.project.young.paymentservice.PaymentServiceMain;
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
        registry.add("payment-service.stub-payment.always-succeed", () -> "false");
        registry.add("payment-service.stub-payment.decline-when-fractional-part", () -> "0.99");
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("TRUNCATE TABLE payments.payment_outbox, payments.payments RESTART IDENTITY CASCADE")
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
}
