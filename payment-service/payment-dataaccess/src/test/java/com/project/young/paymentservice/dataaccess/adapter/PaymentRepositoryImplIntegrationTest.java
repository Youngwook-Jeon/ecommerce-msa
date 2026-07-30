package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.dataaccess.config.PaymentDataAccessConfig;
import com.project.young.paymentservice.dataaccess.mapper.PaymentAggregateMapper;
import com.project.young.paymentservice.dataaccess.mapper.PaymentDataAccessMapper;
import com.project.young.paymentservice.dataaccess.repository.PaymentJpaRepository;
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
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
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
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("TRUNCATE TABLE payments.payment_outbox, payments.payments CASCADE")
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
}
