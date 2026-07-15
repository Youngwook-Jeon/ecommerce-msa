package com.project.young.productservice.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.ProductServiceMain;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.repository.InventoryReservationJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductVariantJpaRepository;
import com.project.young.productservice.web.internal.dto.ReserveInventoryRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inventory reservation API integration tests.
 * <p>
 * Class-level {@code @Transactional} is intentionally omitted: reserve/confirm use
 * {@code REQUIRES_NEW}, and the parallel-reserve scenario needs independent commits.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = ProductServiceMain.class
)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings("resource")
class InventoryReservationApiIntegrationTest {

    private static final String RESERVATIONS = "/internal/inventory/reservations";

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        String jdbcUrl = postgresContainer.getJdbcUrl() + "&currentSchema=product";
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        // Avoid expire scheduler interfering with short IT runs.
        registry.add("product-service.inventory.expire-fixed-delay-ms", () -> "3600000");
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
    private ProductJpaRepository productJpaRepository;
    @Autowired
    private ProductVariantJpaRepository productVariantJpaRepository;
    @Autowired
    private InventoryReservationJpaRepository inventoryReservationJpaRepository;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                    TRUNCATE TABLE
                        inventory_reservations,
                        categories,
                        option_groups,
                        products
                    RESTART IDENTITY CASCADE
                    """).executeUpdate();
            entityManager.flush();
            entityManager.clear();
        });
    }

    @Nested
    @DisplayName("POST /internal/inventory/reservations")
    class ReserveTests {

        @Test
        @WithMockUser
        @DisplayName("reserve: ACTIVE hold를 생성하고 201을 반환한다")
        void reserve_createsActiveHold() throws Exception {
            UUID variantId = persistVariant(5);
            UUID checkoutId = UUID.randomUUID();

            mockMvc.perform(post(RESERVATIONS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request(checkoutId, variantId, 2))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.checkoutId").value(checkoutId.toString()))
                    .andExpect(jsonPath("$.reusedExisting").value(false))
                    .andExpect(jsonPath("$.lines.length()").value(1))
                    .andExpect(jsonPath("$.lines[0].productVariantId").value(variantId.toString()))
                    .andExpect(jsonPath("$.lines[0].quantity").value(2))
                    .andExpect(jsonPath("$.lines[0].status").value("ACTIVE"));

            assertThat(inventoryReservationJpaRepository.findByCheckoutIdOrderByProductVariantIdAsc(checkoutId))
                    .hasSize(1)
                    .first()
                    .extracting("status", "quantity")
                    .containsExactly("ACTIVE", 2);
            assertThat(productVariantJpaRepository.findById(variantId).orElseThrow().getStockQuantity())
                    .isEqualTo(5);
        }

        @Test
        @WithMockUser
        @DisplayName("reserve: available 부족이면 409")
        void reserve_insufficient_returnsConflict() throws Exception {
            UUID variantId = persistVariant(1);
            UUID checkoutId = UUID.randomUUID();

            mockMvc.perform(post(RESERVATIONS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request(checkoutId, variantId, 2))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("Conflict"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient inventory")));
        }

        @Test
        @WithMockUser
        @DisplayName("reserve: 동일 checkout+lines면 ACTIVE를 재사용한다")
        void reserve_sameLines_reusesExisting() throws Exception {
            UUID variantId = persistVariant(5);
            UUID checkoutId = UUID.randomUUID();
            ReserveInventoryRequest body = request(checkoutId, variantId, 1);

            MvcResult first = mockMvc.perform(post(RESERVATIONS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reusedExisting").value(false))
                    .andReturn();

            String firstReservationId = objectMapper.readTree(first.getResponse().getContentAsString())
                    .path("lines").get(0).path("reservationId").asText();

            mockMvc.perform(post(RESERVATIONS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reusedExisting").value(true))
                    .andExpect(jsonPath("$.lines[0].reservationId").value(firstReservationId));

            assertThat(inventoryReservationJpaRepository.findByCheckoutIdOrderByProductVariantIdAsc(checkoutId))
                    .hasSize(1);
        }

        @Test
        @WithMockUser
        @DisplayName("reserve: ACTIVE lines가 다르면 release 후 새로 예약한다")
        void reserve_differentLines_releasesThenRecreates() throws Exception {
            UUID firstVariantId = persistVariant(5);
            UUID secondVariantId = persistVariant(5);
            UUID checkoutId = UUID.randomUUID();

            mockMvc.perform(post(RESERVATIONS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request(checkoutId, firstVariantId, 1))))
                    .andExpect(status().isCreated());

            mockMvc.perform(post(RESERVATIONS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request(checkoutId, secondVariantId, 1))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reusedExisting").value(false))
                    .andExpect(jsonPath("$.lines[0].productVariantId").value(secondVariantId.toString()));

            var rows = inventoryReservationJpaRepository.findByCheckoutIdOrderByProductVariantIdAsc(checkoutId);
            assertThat(rows).hasSize(2);
            assertThat(rows.stream().map(r -> r.getStatus()).toList())
                    .containsExactlyInAnyOrder("RELEASED", "ACTIVE");
        }
    }

    @Nested
    @DisplayName("confirm / release")
    class ConfirmReleaseTests {

        @Test
        @WithMockUser
        @DisplayName("confirm: ACTIVE hold를 확정하고 on-hand를 차감한다")
        void confirm_decreasesOnHand() throws Exception {
            UUID variantId = persistVariant(5);
            UUID checkoutId = UUID.randomUUID();

            mockMvc.perform(post(RESERVATIONS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request(checkoutId, variantId, 2))))
                    .andExpect(status().isCreated());

            mockMvc.perform(post(RESERVATIONS + "/{checkoutId}/confirm", checkoutId))
                    .andExpect(status().isNoContent());

            assertThat(productVariantJpaRepository.findById(variantId).orElseThrow().getStockQuantity())
                    .isEqualTo(3);
            assertThat(inventoryReservationJpaRepository.findByCheckoutIdOrderByProductVariantIdAsc(checkoutId))
                    .first()
                    .extracting("status")
                    .isEqualTo("CONFIRMED");
        }

        @Test
        @WithMockUser
        @DisplayName("confirm: 이미 CONFIRMED면 idempotent하다")
        void confirm_alreadyConfirmed_isIdempotent() throws Exception {
            UUID variantId = persistVariant(5);
            UUID checkoutId = UUID.randomUUID();

            mockMvc.perform(post(RESERVATIONS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request(checkoutId, variantId, 1))))
                    .andExpect(status().isCreated());
            mockMvc.perform(post(RESERVATIONS + "/{checkoutId}/confirm", checkoutId))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post(RESERVATIONS + "/{checkoutId}/confirm", checkoutId))
                    .andExpect(status().isNoContent());

            assertThat(productVariantJpaRepository.findById(variantId).orElseThrow().getStockQuantity())
                    .isEqualTo(4);
        }

        @Test
        @WithMockUser
        @DisplayName("release: ACTIVE hold를 해제하고 on-hand는 유지한다")
        void release_releasesActiveHold() throws Exception {
            UUID variantId = persistVariant(5);
            UUID checkoutId = UUID.randomUUID();

            mockMvc.perform(post(RESERVATIONS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request(checkoutId, variantId, 2))))
                    .andExpect(status().isCreated());

            mockMvc.perform(post(RESERVATIONS + "/{checkoutId}/release", checkoutId))
                    .andExpect(status().isNoContent());

            assertThat(productVariantJpaRepository.findById(variantId).orElseThrow().getStockQuantity())
                    .isEqualTo(5);
            assertThat(inventoryReservationJpaRepository.findByCheckoutIdOrderByProductVariantIdAsc(checkoutId))
                    .first()
                    .extracting("status")
                    .isEqualTo("RELEASED");
        }

        @Test
        @WithMockUser
        @DisplayName("confirm: 예약이 없으면 404")
        void confirm_missing_returnsNotFound() throws Exception {
            mockMvc.perform(post(RESERVATIONS + "/{checkoutId}/confirm", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("동시성")
    class ConcurrencyTests {

        @Test
        @DisplayName("parallel reserve: stock=1이면 두 checkout 중 하나만 성공한다")
        void parallelReserve_stockOne_exactlyOneWins() throws Exception {
            UUID variantId = persistVariant(1);
            UUID checkoutA = UUID.randomUUID();
            UUID checkoutB = UUID.randomUUID();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            AtomicInteger created = new AtomicInteger();
            AtomicInteger conflict = new AtomicInteger();
            AtomicInteger other = new AtomicInteger();

            for (UUID checkoutId : List.of(checkoutA, checkoutB)) {
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        // Request-level auth: @WithMockUser does not propagate to worker threads.
                        MvcResult result = mockMvc.perform(post(RESERVATIONS)
                                        .with(user("inventory-it"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(
                                                request(checkoutId, variantId, 1))))
                                .andReturn();
                        int statusCode = result.getResponse().getStatus();
                        if (statusCode == 201) {
                            created.incrementAndGet();
                        } else if (statusCode == 409) {
                            conflict.incrementAndGet();
                        } else {
                            other.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        other.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();

            assertThat(other.get()).as("unexpected statuses").isZero();
            assertThat(created.get()).isEqualTo(1);
            assertThat(conflict.get()).isEqualTo(1);

            long activeCount = inventoryReservationJpaRepository.findAll().stream()
                    .filter(r -> "ACTIVE".equals(r.getStatus())
                            && r.getProductVariantId().equals(variantId))
                    .count();
            assertThat(activeCount).isEqualTo(1);
            assertThat(productVariantJpaRepository.findById(variantId).orElseThrow().getStockQuantity())
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("카탈로그 available stock")
    class CatalogAvailableStockTests {

        @Test
        @WithMockUser
        @DisplayName("cart-lines search: ACTIVE hold를 반영한 available stock을 반환한다")
        void cartLinesSearch_subtractsActiveHolds() throws Exception {
            UUID variantId = persistVariant(5);
            UUID checkoutId = UUID.randomUUID();

            mockMvc.perform(post(RESERVATIONS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request(checkoutId, variantId, 2))))
                    .andExpect(status().isCreated());

            String searchBody = """
                    {"productVariantIds":["%s"]}
                    """.formatted(variantId);

            MvcResult result = mockMvc.perform(post("/public/catalog/cart-lines/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(searchBody))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode lines = objectMapper.readTree(result.getResponse().getContentAsString()).path("lines");
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).path("productVariantId").asText()).isEqualTo(variantId.toString());
            assertThat(lines.get(0).path("stockQuantity").asInt()).isEqualTo(3);
        }
    }

    private UUID persistVariant(int stockQuantity) {
        return transactionTemplate.execute(status -> {
            ProductEntity product = ProductEntity.builder()
                    .id(UUID.randomUUID())
                    .name("Inventory IT Product")
                    .description("Integration test product description.")
                    .basePrice(new BigDecimal("10000"))
                    .status(ProductStatusEntity.ACTIVE)
                    .conditionType(ConditionTypeEntity.NEW)
                    .brand("Brand")
                    .mainImageUrl("https://example.com/main.jpg")
                    .build();
            UUID variantId = UUID.randomUUID();
            ProductVariantEntity variant = ProductVariantEntity.builder()
                    .id(variantId)
                    .sku("SKU-" + variantId)
                    .stockQuantity(stockQuantity)
                    .status(ProductStatusEntity.ACTIVE)
                    .calculatedPrice(new BigDecimal("10000"))
                    .build();
            product.addVariant(variant);
            productJpaRepository.saveAndFlush(product);
            entityManager.clear();
            return variantId;
        });
    }

    private static ReserveInventoryRequest request(UUID checkoutId, UUID variantId, int quantity) {
        return new ReserveInventoryRequest(
                checkoutId,
                List.of(new ReserveInventoryRequest.Line(variantId, quantity))
        );
    }
}
