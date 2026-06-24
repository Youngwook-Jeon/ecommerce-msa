package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.config.OrderDataAccessConfig;
import com.project.young.orderservice.dataaccess.entity.CartEntity;
import com.project.young.orderservice.dataaccess.entity.CartItemEntity;
import com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.CART_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.ITEM_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.PRODUCT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.USER_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.VARIANT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.persistedCartEntity;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.persistedItemEntity;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = CartJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CartJpaRepositoryTest {

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
    registry.add("spring.jpa.properties.hibernate.format_sql", () -> "true");
    registry.add("spring.jpa.show-sql", () -> "true");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
  }

  @Autowired
  private CartJpaRepository cartJpaRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @BeforeEach
  void setUp() {
    CartJpaRepositoryTestFixtures.truncateCartTables(testEntityManager);
  }

  @Nested
  @DisplayName("findByUserId 테스트")
  class FindByUserIdTests {

    @Test
    @DisplayName("user_id로 카트와 아이템을 함께 조회한다")
    void findByUserId_loadsItems() {
      persistSampleCart();

      Optional<CartEntity> found = cartJpaRepository.findByUserId(USER_ID.value());

      assertThat(found).isPresent();
      assertThat(found.get().getId()).isEqualTo(CART_ID.getValue());
      assertThat(Hibernate.isInitialized(found.get().getItems())).isTrue();
      assertThat(found.get().getItems()).hasSize(1);
      assertThat(found.get().getItems().get(0).getProductVariantId()).isEqualTo(VARIANT_ID.getValue());
    }

    @Test
    @DisplayName("존재하지 않는 user_id는 빈 Optional을 반환한다")
    void findByUserId_notFound_returnsEmpty() {
      assertThat(cartJpaRepository.findByUserId("missing-user")).isEmpty();
    }
  }

  @Nested
  @DisplayName("findWithItemsById 테스트")
  class FindWithItemsByIdTests {

    @Test
    @DisplayName("cart id로 카트와 아이템을 함께 조회한다")
    void findWithItemsById_loadsItems() {
      persistSampleCart();

      Optional<CartEntity> found = cartJpaRepository.findWithItemsById(CART_ID.getValue());

      assertThat(found).isPresent();
      assertThat(Hibernate.isInitialized(found.get().getItems())).isTrue();
      assertThat(found.get().getItems()).extracting(CartItemEntity::getId)
          .containsExactly(ITEM_ID.getValue());
    }

    @Test
    @DisplayName("존재하지 않는 cart id는 빈 Optional을 반환한다")
    void findWithItemsById_notFound_returnsEmpty() {
      assertThat(cartJpaRepository.findWithItemsById(UUID.randomUUID())).isEmpty();
    }
  }

  private void persistSampleCart() {
    CartEntity cart = persistedCartEntity(
        CART_ID.getValue(),
        USER_ID.value(),
        List.of(persistedItemEntity(
            ITEM_ID.getValue(),
            PRODUCT_ID.getValue(),
            VARIANT_ID.getValue(),
            "iPhone 15 Pro",
            new BigDecimal("999.00"),
            2
        ))
    );
    testEntityManager.persistAndFlush(cart);
    testEntityManager.clear();
  }

  @Configuration
  @Import(OrderDataAccessConfig.class)
  static class Config {
  }
}
