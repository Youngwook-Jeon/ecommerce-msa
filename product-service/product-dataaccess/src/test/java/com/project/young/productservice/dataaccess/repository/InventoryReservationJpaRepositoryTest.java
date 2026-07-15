package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.InventoryReservationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = InventoryReservationJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings("resource")
class InventoryReservationJpaRepositoryTest {

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
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private InventoryReservationJpaRepository repository;
    @Autowired
    private TestEntityManager testEntityManager;

    private UUID variantId;

    @BeforeEach
    void setUp() {
        JpaRepositoryTestFixtures.truncateCompositionTables(testEntityManager);
        variantId = JpaRepositoryTestFixtures.persistColorSizeProduct(testEntityManager).variantId();
    }

    @Test
    @DisplayName("findByCheckoutIdOrderByProductVariantIdAsc: checkout reservation을 variant id 순으로 조회한다")
    void findByCheckoutIdOrdersByVariantId() {
        UUID checkoutId = UUID.randomUUID();
        UUID firstVariantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondVariantId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        duplicateVariant(firstVariantId, "SKU-ORDER-1");
        duplicateVariant(secondVariantId, "SKU-ORDER-2");
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        repository.saveAll(List.of(
                reservation(checkoutId, secondVariantId, 1, "ACTIVE", now.plusSeconds(900), now),
                reservation(checkoutId, firstVariantId, 1, "ACTIVE", now.plusSeconds(900), now)
        ));
        repository.flush();
        testEntityManager.clear();

        assertThat(repository.findByCheckoutIdOrderByProductVariantIdAsc(checkoutId))
                .extracting(InventoryReservationEntity::getProductVariantId)
                .containsExactly(firstVariantId, secondVariantId);
    }

    @Test
    @DisplayName("sumActiveQuantityByVariantId: 만료되지 않은 ACTIVE 수량만 합산한다")
    void sumActiveQuantityIncludesOnlyUnexpiredActiveRows() {
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        repository.saveAll(List.of(
                reservation(UUID.randomUUID(), variantId, 3, "ACTIVE", now.plusSeconds(1), now),
                reservation(UUID.randomUUID(), variantId, 5, "ACTIVE", now, now.minusSeconds(900)),
                reservation(UUID.randomUUID(), variantId, 7, "ACTIVE", now.minusSeconds(1), now.minusSeconds(900)),
                reservation(UUID.randomUUID(), variantId, 11, "CONFIRMED", now.plusSeconds(900), now)
        ));
        repository.flush();

        assertThat(repository.sumActiveQuantityByVariantId(variantId, now)).isEqualTo(3);
    }

    @Test
    @DisplayName("sumActiveQuantityByVariantIds: variant별 유효 ACTIVE 수량을 한 번에 집계한다")
    void sumActiveQuantityGroupsByVariant() {
        UUID otherVariantId = UUID.randomUUID();
        duplicateVariant(otherVariantId, "SKU-SUM-OTHER");
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        repository.saveAll(List.of(
                reservation(UUID.randomUUID(), variantId, 2, "ACTIVE", now.plusSeconds(900), now),
                reservation(UUID.randomUUID(), variantId, 3, "ACTIVE", now.plusSeconds(900), now),
                reservation(UUID.randomUUID(), otherVariantId, 4, "ACTIVE", now.plusSeconds(900), now),
                reservation(UUID.randomUUID(), otherVariantId, 8, "RELEASED", now.plusSeconds(900), now)
        ));
        repository.flush();

        Map<UUID, Integer> sums = repository.sumActiveQuantityByVariantIds(
                        List.of(variantId, otherVariantId),
                        now
                ).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).intValue()));

        assertThat(sums).containsExactlyInAnyOrderEntriesOf(Map.of(variantId, 5, otherVariantId, 4));
    }

    @Test
    @DisplayName("findDueActiveForUpdateSkipLocked: 만료 대상 ACTIVE만 오래된 순서와 limit에 맞춰 조회한다")
    void findDueActiveForUpdateFiltersOrdersAndLimits() {
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        InventoryReservationEntity oldest =
                reservation(UUID.randomUUID(), variantId, 1, "ACTIVE", now.minusSeconds(20), now.minusSeconds(900));
        InventoryReservationEntity next =
                reservation(UUID.randomUUID(), variantId, 1, "ACTIVE", now.minusSeconds(10), now.minusSeconds(900));
        repository.saveAll(List.of(
                next,
                oldest,
                reservation(UUID.randomUUID(), variantId, 1, "ACTIVE", now.plusSeconds(1), now),
                reservation(UUID.randomUUID(), variantId, 1, "EXPIRED", now.minusSeconds(30), now.minusSeconds(900))
        ));
        repository.flush();
        testEntityManager.clear();

        assertThat(repository.findDueActiveForUpdateSkipLocked(now, 1))
                .extracting(InventoryReservationEntity::getId)
                .containsExactly(oldest.getId());
    }

    @Test
    @DisplayName("V8 partial unique index: 같은 checkout/variant에는 ACTIVE를 하나만 허용한다")
    void activeUniqueIndexRejectsDuplicateActiveRows() {
        UUID checkoutId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        repository.saveAndFlush(reservation(checkoutId, variantId, 1, "ACTIVE", now.plusSeconds(900), now));

        assertThatThrownBy(() ->
                repository.saveAndFlush(reservation(checkoutId, variantId, 2, "ACTIVE", now.plusSeconds(900), now)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_inventory_reservations_active_checkout_variant");
    }

    @Test
    @DisplayName("V8 partial unique index: RELEASED 이력 뒤에는 같은 checkout/variant를 재예약할 수 있다")
    void activeUniqueIndexAllowsRereserveAfterRelease() {
        UUID checkoutId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        repository.saveAndFlush(reservation(checkoutId, variantId, 1, "RELEASED", now.plusSeconds(900), now));

        InventoryReservationEntity active =
                repository.saveAndFlush(reservation(checkoutId, variantId, 2, "ACTIVE", now.plusSeconds(900), now));

        assertThat(repository.findByCheckoutIdOrderByProductVariantIdAsc(checkoutId))
                .extracting(InventoryReservationEntity::getStatus)
                .containsExactlyInAnyOrder("RELEASED", "ACTIVE");
        assertThat(active.getVersion()).isZero();
    }

    @Test
    @DisplayName("@Version: reservation 상태 변경 시 version이 증가한다")
    void versionIncrementsOnUpdate() {
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        InventoryReservationEntity entity = repository.saveAndFlush(
                reservation(UUID.randomUUID(), variantId, 1, "ACTIVE", now.plusSeconds(900), now));
        assertThat(entity.getVersion()).isZero();

        entity.setStatus("CONFIRMED");
        repository.flush();

        assertThat(entity.getVersion()).isEqualTo(1);
    }

    private void duplicateVariant(UUID id, String sku) {
        testEntityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO product_variants (
                            id, product_id, sku, stock_quantity, version, status,
                            calculated_price, created_at, updated_at
                        )
                        SELECT :id, product_id, :sku, stock_quantity, 0, status,
                               calculated_price, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        FROM product_variants
                        WHERE id = :sourceId
                        """)
                .setParameter("id", id)
                .setParameter("sku", sku)
                .setParameter("sourceId", variantId)
                .executeUpdate();
        testEntityManager.flush();
        testEntityManager.clear();
    }

    private static InventoryReservationEntity reservation(
            UUID checkoutId,
            UUID productVariantId,
            int quantity,
            String status,
            Instant expiresAt,
            Instant createdAt
    ) {
        return InventoryReservationEntity.builder()
                .id(UUID.randomUUID())
                .checkoutId(checkoutId)
                .productVariantId(productVariantId)
                .quantity(quantity)
                .status(status)
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    @Configuration
    @Import(ProductDataAccessConfig.class)
    static class Config {
    }
}
