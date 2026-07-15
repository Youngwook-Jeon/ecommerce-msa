package com.project.young.orderservice.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.orderservice.OrderServiceMain;
import com.project.young.orderservice.it.support.CatalogTestRestClientHolder;
import com.project.young.orderservice.it.support.InventoryTestRestClientHolder;
import com.project.young.orderservice.it.support.OrderIntegrationTestConfiguration;
import com.project.young.orderservice.it.support.ProductCatalogTestSupport;
import com.project.young.orderservice.it.support.ProductCatalogTestSupport.CatalogLineStub;
import com.project.young.orderservice.web.cart.dto.AddCartItemRequest;
import com.project.young.orderservice.application.dto.command.PlaceOrderCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.UUID;

import static com.project.young.orderservice.it.support.InventoryReservationTestSupport.stubReserveSuccess;
import static com.project.young.orderservice.it.support.ProductCatalogTestSupport.stubCatalogLines;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = OrderServiceMain.class
)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderIntegrationTestConfiguration.class)
@Transactional
class OrderApiIntegrationTest {

    private static final String USER_SUBJECT = "018f0000-0000-7000-8000-000000000101";
    private static final UUID PRODUCT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000301");
    private static final UUID VARIANT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000401");

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        String jdbcUrl = postgresContainer.getJdbcUrl() + "&currentSchema=orders";
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CatalogTestRestClientHolder catalogTestRestClientHolder;

    @Autowired
    private InventoryTestRestClientHolder inventoryTestRestClientHolder;

    private MockRestServiceServer catalogServer;
    private MockRestServiceServer inventoryServer;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("TRUNCATE TABLE orders RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE carts RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.flush();
        entityManager.clear();

        catalogServer = catalogTestRestClientHolder.mockServer();
        catalogServer.reset();
        inventoryServer = inventoryTestRestClientHolder.mockServer();
        inventoryServer.reset();
    }

    @Nested
    @DisplayName("POST /orders")
    class PlaceOrder {

        @Test
        @DisplayName("비인증 요청은 401")
        void unauthenticated_returnsUnauthorized() throws Exception {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(placeOrderRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("빈 카트면 400")
        void emptyCart_returnsBadRequest() throws Exception {
            mockMvc.perform(post("/orders")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(placeOrderRequest())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("카트 스냅샷으로 PENDING_PAYMENT 주문을 생성하고 카트는 유지한다")
        void authenticatedUser_placesOrderAndKeepsCart() throws Exception {
            stubCatalogLines(catalogServer, catalogLine("Phone", "100.00", 5));
            stubReserveSuccess(inventoryServer);
            addItemAsUser(2);

            MvcResult placeResult = mockMvc.perform(post("/orders")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(placeOrderRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.userId").value(USER_SUBJECT))
                    .andExpect(jsonPath("$.shippingAmount").value(0))
                    .andExpect(jsonPath("$.subtotal").value(200.00))
                    .andExpect(jsonPath("$.totalAmount").value(200.00))
                    .andExpect(jsonPath("$.lines", hasSize(1)))
                    .andExpect(jsonPath("$.lines[0].productName").value("Phone"))
                    .andExpect(jsonPath("$.lines[0].quantity").value(2))
                    .andExpect(jsonPath("$.shippingAddress.recipientName").value("Kim Young"))
                    .andReturn();

            String orderId = objectMapper.readTree(placeResult.getResponse().getContentAsString())
                    .path("orderId").asText();

            mockMvc.perform(get("/carts/current")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemCount").value(1));

            mockMvc.perform(get("/orders/{orderId}", orderId)
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(orderId))
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        }
    }

    @Nested
    @DisplayName("GET /orders/{orderId}")
    class GetOrder {

        @Test
        @DisplayName("다른 사용자 주문은 404")
        void otherUser_returnsNotFound() throws Exception {
            stubCatalogLines(catalogServer, catalogLine("Phone", "100.00", 5));
            stubReserveSuccess(inventoryServer);
            addItemAsUser(1);

            MvcResult placeResult = mockMvc.perform(post("/orders")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(placeOrderRequest())))
                    .andExpect(status().isCreated())
                    .andReturn();

            String orderId = objectMapper.readTree(placeResult.getResponse().getContentAsString())
                    .path("orderId").asText();

            mockMvc.perform(get("/orders/{orderId}", orderId)
                            .with(jwt().jwt(builder -> builder.subject("other-user-subject"))))
                    .andExpect(status().isNotFound());
        }
    }

    private CatalogLineStub catalogLine(String productName, String unitPrice, int stockQuantity) {
        return CatalogLineStub.available(
                PRODUCT_ID,
                VARIANT_ID,
                productName,
                new BigDecimal(unitPrice),
                stockQuantity
        );
    }

    private AddCartItemRequest addItemRequest(int quantity) {
        return AddCartItemRequest.builder()
                .productId(PRODUCT_ID)
                .productVariantId(VARIANT_ID)
                .quantity(quantity)
                .build();
    }

    private void addItemAsUser(int quantity) throws Exception {
        mockMvc.perform(post("/carts/current/items")
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemRequest(quantity))))
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
