package com.project.young.orderservice.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.orderservice.OrderServiceMain;
import com.project.young.orderservice.it.support.CatalogTestRestClientHolder;
import com.project.young.orderservice.it.support.OrderIntegrationTestConfiguration;
import com.project.young.orderservice.it.support.ProductCatalogTestSupport;
import com.project.young.orderservice.it.support.ProductCatalogTestSupport.CatalogLineStub;
import com.project.young.orderservice.web.cart.dto.AddCartItemRequest;
import com.project.young.orderservice.web.cart.dto.UpdateCartItemQuantityRequest;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static com.project.young.orderservice.it.support.ProductCatalogTestSupport.stubCatalogLines;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
class CartApiIntegrationTest {

    private static final String USER_SUBJECT = "018f0000-0000-7000-8000-000000000101";
    private static final UUID PRODUCT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000301");
    private static final UUID VARIANT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000401");
    private static final UUID SECOND_VARIANT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000402");

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

    private MockRestServiceServer catalogServer;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("TRUNCATE TABLE carts RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.flush();
        entityManager.clear();

        catalogServer = catalogTestRestClientHolder.mockServer();
        catalogServer.reset();
    }

    @Nested
    @DisplayName("GET /carts/current")
    class GetCurrentCart {

        @Test
        @DisplayName("게스트 쿠키 없으면 빈 게스트 카트를 반환한다")
        void guestWithoutCookie_returnsEmptyGuestCart() throws Exception {
            mockMvc.perform(get("/carts/current"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ownerType").value("GUEST"))
                    .andExpect(jsonPath("$.itemCount").value(0))
                    .andExpect(jsonPath("$.totalQuantity").value(0));
        }

        @Test
        @DisplayName("인증 사용자는 사용자 카트를 반환한다")
        void authenticatedUser_returnsUserCart() throws Exception {
            stubCatalogLines(catalogServer, catalogLine("Phone", "100.00", 5));
            addItemAsUser(1);

            mockMvc.perform(get("/carts/current")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ownerType").value("USER"))
                    .andExpect(jsonPath("$.userId").value(USER_SUBJECT))
                    .andExpect(jsonPath("$.itemCount").value(1))
                    .andExpect(jsonPath("$.items[0].productVariantId").value(VARIANT_ID.toString()));
        }
    }

    @Nested
    @DisplayName("POST /carts/current/items")
    class AddItem {

        @Test
        @DisplayName("게스트 첫 추가 시 쿠키를 발급하고 라인을 저장한다")
        void guestFirstAdd_setsCookieAndPersistsLine() throws Exception {
            stubCatalogLines(catalogServer, catalogLine("Phone", "100.00", 5));

            mockMvc.perform(post("/carts/current/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addItemRequest(2))))
                    .andExpect(status().isOk())
                    .andExpect(cookie().exists("guest_cart_id"))
                    .andExpect(jsonPath("$.ownerType").value("GUEST"))
                    .andExpect(jsonPath("$.itemCount").value(1))
                    .andExpect(jsonPath("$.totalQuantity").value(2))
                    .andExpect(jsonPath("$.items[0].productName").value("Phone"))
                    .andExpect(jsonPath("$.items[0].unitPrice").value(100.00));
        }

        @Test
        @DisplayName("인증 사용자는 사용자 카트에 라인을 추가한다")
        void authenticatedUser_addsToUserCart() throws Exception {
            stubCatalogLines(catalogServer, catalogLine("Phone", "100.00", 5));

            mockMvc.perform(post("/carts/current/items")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addItemRequest(1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ownerType").value("USER"))
                    .andExpect(jsonPath("$.userId").value(USER_SUBJECT))
                    .andExpect(jsonPath("$.itemCount").value(1));
        }

        @Test
        @DisplayName("재고보다 많은 수량이면 400")
        void insufficientStock_returnsBadRequest() throws Exception {
            stubCatalogLines(catalogServer, catalogLine("Phone", "100.00", 1));

            mockMvc.perform(post("/carts/current/items")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addItemRequest(3))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PATCH / DELETE /carts/current/items")
    class MutateItems {

        @Test
        @DisplayName("수량 변경, 단일 삭제, 전체 비우기")
        void updateRemoveAndClear() throws Exception {
            stubCatalogLines(catalogServer,
                    catalogLine("Phone", "100.00", 10),
                    CatalogLineStub.available(
                            PRODUCT_ID, SECOND_VARIANT_ID, "Case", new BigDecimal("20.00"), 10));
            UUID itemId = addItemAsUser(2);

            mockMvc.perform(patch("/carts/current/items/{itemId}", itemId)
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    UpdateCartItemQuantityRequest.builder().quantity(5).build())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].quantity").value(5));

            mockMvc.perform(delete("/carts/current/items/{itemId}", itemId)
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemCount").value(0));

            addItemAsUser(1);
            addItemAsUser(SECOND_VARIANT_ID, 1);

            mockMvc.perform(delete("/carts/current/items")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemCount").value(0))
                    .andExpect(jsonPath("$.totalQuantity").value(0));
        }
    }

    @Nested
    @DisplayName("POST /carts/current/sync")
    class SyncCart {

        @Test
        @DisplayName("catalog 가격 변경을 반영한다")
        void sync_appliesCatalogPriceChange() throws Exception {
            stubCatalogLines(catalogServer, ExpectedCount.once(), catalogLine("Phone", "100.00", 5));
            stubCatalogLines(catalogServer, ExpectedCount.once(), catalogLine("Phone", "120.00", 5));
            addItemAsUser(1);

            mockMvc.perform(post("/carts/current/sync")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.changes", hasSize(1)))
                    .andExpect(jsonPath("$.changes[0].type").value("PRICE_UPDATED"))
                    .andExpect(jsonPath("$.cart.items[0].unitPrice").value(120.00));
        }
    }

    @Nested
    @DisplayName("POST /carts/current/merge")
    class MergeGuestCart {

        @Test
        @DisplayName("게스트 카트를 사용자 카트로 병합하고 쿠키를 만료한다")
        void mergeGuestCartIntoUserCart() throws Exception {
            stubCatalogLines(catalogServer, catalogLine("Phone", "100.00", 5));
            Cookie guestCookie = addItemAsGuest(2);

            mockMvc.perform(post("/carts/current/merge")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                            .cookie(guestCookie))
                    .andExpect(status().isOk())
                    .andExpect(cookie().maxAge("guest_cart_id", 0))
                    .andExpect(jsonPath("$.mergedLineCount").value(1))
                    .andExpect(jsonPath("$.cart.ownerType").value("USER"))
                    .andExpect(jsonPath("$.cart.userId").value(USER_SUBJECT))
                    .andExpect(jsonPath("$.cart.totalQuantity").value(2));

            mockMvc.perform(get("/carts/current")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemCount").value(1))
                    .andExpect(jsonPath("$.totalQuantity").value(2));
        }

        @Test
        @DisplayName("게스트 쿠키 없으면 병합 없이 현재 사용자 카트를 반환한다")
        void withoutGuestCookie_returnsCurrentUserCart() throws Exception {
            stubCatalogLines(catalogServer, catalogLine("Phone", "100.00", 5));
            addItemAsUser(1);

            mockMvc.perform(post("/carts/current/merge")
                            .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mergedLineCount").value(0))
                    .andExpect(jsonPath("$.cart.ownerType").value("USER"))
                    .andExpect(jsonPath("$.cart.itemCount").value(1));
        }

        @Test
        @DisplayName("비인증 요청은 401")
        void unauthenticated_returnsUnauthorized() throws Exception {
            mockMvc.perform(post("/carts/current/merge"))
                    .andExpect(status().isUnauthorized());
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
        return addItemRequest(VARIANT_ID, quantity);
    }

    private AddCartItemRequest addItemRequest(UUID variantId, int quantity) {
        return AddCartItemRequest.builder()
                .productId(PRODUCT_ID)
                .productVariantId(variantId)
                .quantity(quantity)
                .build();
    }

    private UUID addItemAsUser(int quantity) throws Exception {
        return addItemAsUser(VARIANT_ID, quantity);
    }

    private UUID addItemAsUser(UUID variantId, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/carts/current/items")
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemRequest(variantId, quantity))))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("items").get(0).path("itemId").asText()
        );
    }

    private Cookie addItemAsGuest(int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/carts/current/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemRequest(quantity))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("guest_cart_id");
        if (cookie == null) {
            throw new IllegalStateException("guest_cart_id cookie was not issued");
        }
        return cookie;
    }
}
