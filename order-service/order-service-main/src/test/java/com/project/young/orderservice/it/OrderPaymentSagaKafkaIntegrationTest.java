package com.project.young.orderservice.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.common.application.contract.payment.PaymentReconciliationStatus;
import com.project.young.orderservice.OrderServiceMain;
import com.project.young.orderservice.application.dto.PaymentStatusSnapshot;
import com.project.young.orderservice.application.dto.command.PlaceOrderCommand;
import com.project.young.orderservice.application.service.OrderPaymentReconciliationExecutor;
import com.project.young.orderservice.it.support.CatalogTestRestClientHolder;
import com.project.young.orderservice.it.support.InventoryTestRestClientHolder;
import com.project.young.orderservice.it.support.KafkaJsonProducerSupport;
import com.project.young.orderservice.it.support.OrderIntegrationTestConfiguration;
import com.project.young.orderservice.it.support.PaymentStatusQueryTestHolder;
import com.project.young.orderservice.it.support.ProductCatalogTestSupport.CatalogLineStub;
import com.project.young.orderservice.web.cart.dto.AddCartItemRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.project.young.orderservice.it.support.InventoryReservationTestSupport.stubConfirmSuccess;
import static com.project.young.orderservice.it.support.InventoryReservationTestSupport.stubReleaseSuccess;
import static com.project.young.orderservice.it.support.InventoryReservationTestSupport.stubReserveSuccess;
import static com.project.young.orderservice.it.support.ProductCatalogTestSupport.stubCatalogLines;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = OrderServiceMain.class
)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderIntegrationTestConfiguration.class)
class OrderPaymentSagaKafkaIntegrationTest {

    private static final String USER_SUBJECT = "018f0000-0000-7000-8000-000000000101";
    private static final UUID PRODUCT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000301");
    private static final UUID VARIANT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000401");

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        String jdbcUrl = postgresContainer.getJdbcUrl() + "&currentSchema=orders";
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("kafka-config.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("order-service.saga-events.enabled", () -> "true");
        registry.add("order-service.saga-events.payment-completed-topic", () -> "payment.completed");
        registry.add("order-service.saga-events.payment-failed-topic", () -> "payment.failed");
        registry.add(
                "order-service.saga-events.payment-completed-consumer-group",
                () -> "order-it-payment-completed"
        );
        registry.add(
                "order-service.saga-events.payment-failed-consumer-group",
                () -> "order-it-payment-failed"
        );
        registry.add("order-service.saga-events.payment-status-reconciliation.max-attempts", () -> "1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private CatalogTestRestClientHolder catalogTestRestClientHolder;

    @Autowired
    private InventoryTestRestClientHolder inventoryTestRestClientHolder;

    @Autowired
    private PaymentStatusQueryTestHolder paymentStatusQuery;

    @Autowired
    private OrderPaymentReconciliationExecutor reconciliationExecutor;

    private MockRestServiceServer catalogServer;
    private MockRestServiceServer inventoryServer;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("TRUNCATE TABLE orders RESTART IDENTITY CASCADE").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE carts RESTART IDENTITY CASCADE").executeUpdate();
            entityManager.flush();
            entityManager.clear();
        });

        catalogServer = catalogTestRestClientHolder.mockServer();
        catalogServer.reset();
        inventoryServer = inventoryTestRestClientHolder.mockServer();
        inventoryServer.reset();
        paymentStatusQuery.clear();
    }

    @Test
    @DisplayName("payment.completed JSON을 소비하면 주문을 CONFIRMED로 바꾸고 재고를 확정한다")
    void paymentCompleted_confirmsOrderAndInventory() throws Exception {
        stubCatalogLines(catalogServer, catalogLine());
        stubReserveSuccess(inventoryServer);
        addItemAsUser(2);
        UUID orderId = placePendingPaymentOrder();

        inventoryServer.reset();
        stubConfirmSuccess(inventoryServer, orderId);

        KafkaJsonProducerSupport.send(
                kafkaContainer.getBootstrapServers(),
                "payment.completed",
                orderId.toString(),
                KafkaJsonProducerSupport.paymentCompletedJson(orderId, USER_SUBJECT, "200.00")
        );

        await().atMost(KafkaJsonProducerSupport.awaitTimeout()).untilAsserted(() ->
                mockMvc.perform(get("/orders/{orderId}", orderId)
                                .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        );

        mockMvc.perform(get("/carts/current")
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0));
        inventoryServer.verify();
    }

    @Test
    @DisplayName("payment.failed JSON을 소비하면 주문을 CANCELLED로 바꾸고 재고를 해제한다")
    void paymentFailed_cancelsOrderAndReleasesInventory() throws Exception {
        stubCatalogLines(catalogServer, catalogLine());
        stubReserveSuccess(inventoryServer);
        addItemAsUser(1);
        UUID orderId = placePendingPaymentOrder();

        inventoryServer.reset();
        stubReleaseSuccess(inventoryServer, orderId);

        KafkaJsonProducerSupport.send(
                kafkaContainer.getBootstrapServers(),
                "payment.failed",
                orderId.toString(),
                KafkaJsonProducerSupport.paymentFailedJson(orderId, USER_SUBJECT, "100.00", "card declined")
        );

        await().atMost(KafkaJsonProducerSupport.awaitTimeout()).untilAsserted(() ->
                mockMvc.perform(get("/orders/{orderId}", orderId)
                                .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("CANCELLED"))
        );

        mockMvc.perform(get("/carts/current")
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1));
        inventoryServer.verify();
    }

    @Test
    @DisplayName("결제 완료 후 주문 확정이 반복 실패하면 retry/DLT 소비 결과를 MANUAL 보상으로 영속화한다")
    void paymentCompleted_whenOrderConfirmationFails_persistsManualDltCompensation() throws Exception {
        stubCatalogLines(catalogServer, catalogLine());
        stubReserveSuccess(inventoryServer);
        addItemAsUser(1);
        UUID orderId = placePendingPaymentOrder();

        inventoryServer.reset();
        inventoryServer.expect(org.springframework.test.web.client.ExpectedCount.manyTimes(), requestTo(
                        "http://inventory.test/internal/inventory/reservations/" + orderId + "/confirm"))
                .andRespond(withServerError());
        KafkaJsonProducerSupport.send(
                kafkaContainer.getBootstrapServers(),
                "payment.completed",
                orderId.toString(),
                KafkaJsonProducerSupport.paymentCompletedJson(orderId, USER_SUBJECT, "100.00")
        );

        await().atMost(KafkaJsonProducerSupport.awaitTimeout()).untilAsserted(() -> {
            Number compensationCount = transactionTemplate.execute(status -> (Number) entityManager.createNativeQuery("""
                            SELECT COUNT(*) FROM saga_compensation
                            WHERE order_id = CAST(:orderId AS uuid) AND handling_status = 'MANUAL'
                            """)
                    .setParameter("orderId", orderId)
                    .getSingleResult());
            org.assertj.core.api.Assertions.assertThat(compensationCount.longValue()).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("오래된 PENDING_PAYMENT는 batch reconciliation으로 확정하거나 실패 시 ESCALATED로 보관한다")
    void stalePendingPayment_batchReconciliationConvergesOrEscalates() throws Exception {
        UUID convergedOrderId = placeStalePendingOrder();
        paymentStatusQuery.put(new PaymentStatusSnapshot(
                UUID.randomUUID(), convergedOrderId, PaymentReconciliationStatus.COMPLETED, Instant.now()));
        inventoryServer.reset();
        stubConfirmSuccess(inventoryServer, convergedOrderId);

        reconciliationExecutor.reconcileStalePendingPayments();

        await().untilAsserted(() -> mockMvc.perform(get("/orders/{orderId}", convergedOrderId)
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED")));

        catalogServer.reset();
        inventoryServer.reset();
        UUID escalatedOrderId = placeStalePendingOrder();
        paymentStatusQuery.put(new PaymentStatusSnapshot(
                UUID.randomUUID(), escalatedOrderId, PaymentReconciliationStatus.COMPLETED, Instant.now()));
        inventoryServer.reset();
        inventoryServer.expect(once(), requestTo(
                        "http://inventory.test/internal/inventory/reservations/" + escalatedOrderId + "/confirm"))
                .andRespond(withServerError());

        reconciliationExecutor.reconcileStalePendingPayments();

        await().untilAsserted(() -> {
            String handlingStatus = transactionTemplate.execute(status -> (String) entityManager.createNativeQuery("""
                            SELECT CAST(handling_status AS varchar)
                            FROM order_payment_reconciliation_failures
                            WHERE order_id = CAST(:orderId AS uuid)
                            """)
                    .setParameter("orderId", escalatedOrderId)
                    .getSingleResult());
            org.assertj.core.api.Assertions.assertThat(handlingStatus).isEqualTo("ESCALATED");
        });
        inventoryServer.verify();
    }

    private UUID placeStalePendingOrder() throws Exception {
        stubCatalogLines(catalogServer, catalogLine());
        stubReserveSuccess(inventoryServer);
        addItemAsUser(1);
        UUID orderId = placePendingPaymentOrder();
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery("""
                        UPDATE orders SET updated_at = now() - interval '10 minutes'
                        WHERE id = CAST(:orderId AS uuid)
                        """)
                .setParameter("orderId", orderId)
                .executeUpdate());
        return orderId;
    }

    private CatalogLineStub catalogLine() {
        return CatalogLineStub.available(PRODUCT_ID, VARIANT_ID, "Phone", new BigDecimal("100.00"), 5);
    }

    private UUID placePendingPaymentOrder() throws Exception {
        MvcResult placeResult = mockMvc.perform(post("/orders")
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(placeOrderRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(placeResult.getResponse().getContentAsString())
                .path("orderId").asText());
    }

    private void addItemAsUser(int quantity) throws Exception {
        mockMvc.perform(post("/carts/current/items")
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AddCartItemRequest.builder()
                                .productId(PRODUCT_ID)
                                .productVariantId(VARIANT_ID)
                                .quantity(quantity)
                                .build())))
                .andExpect(status().isOk());
    }

    private PlaceOrderCommand placeOrderRequest() {
        return PlaceOrderCommand.builder()
                .recipientName("Kim Young")
                .phone("01012345678")
                .addressLine1("123 Main St")
                .addressLine2("Apt 4B")
                .city("Seoul")
                .postalCode("04524")
                .countryCode("KR")
                .build();
    }
}
