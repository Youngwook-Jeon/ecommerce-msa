package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.dataaccess.config.OrderDataAccessConfig;
import com.project.young.orderservice.dataaccess.mapper.CartAggregateMapper;
import com.project.young.orderservice.dataaccess.mapper.CartDataAccessMapper;
import com.project.young.orderservice.dataaccess.repository.CartJpaRepository;
import com.project.young.orderservice.dataaccess.repository.CartJpaRepositoryTestFixtures;
import com.project.young.orderservice.dataaccess.support.SqlStatementCounter;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.util.List;
import java.util.UUID;

import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.CART_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.ITEM_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.PRODUCT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.USER_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.VARIANT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.domainItem;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.sampleSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = CartRepositoryImplIntegrationTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CartRepositoryImplIntegrationTest {

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
    registry.add(
        "spring.jpa.properties.hibernate.session_factory.statement_inspector",
        () -> SqlStatementCounter.class.getName()
    );
  }

  @Autowired
  private CartRepositoryImpl cartRepository;

  @Autowired
  private CartJpaRepository cartJpaRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @BeforeEach
  void setUp() {
    CartJpaRepositoryTestFixtures.truncateCartTables(testEntityManager);
    SqlStatementCounter.reset();
  }

  @Test
  @DisplayName("update: 제거된 cart item은 개별 DELETE 문으로 flush된다")
  void update_removedItemsAreDeletedOneStatementPerRow() {
    UUID removedVariant1 = UUID.fromString("018f0000-0000-7000-8000-000000000291");
    UUID removedVariant2 = UUID.fromString("018f0000-0000-7000-8000-000000000292");

    Cart seedCart = Cart.builder()
        .cartId(CART_ID)
        .userId(USER_ID)
        .items(List.of(
            domainItem(ITEM_ID, PRODUCT_ID, VARIANT_ID, sampleSnapshot("100.00"), 1),
            domainItem(
                new CartItemId(UUID.fromString("018f0000-0000-7000-8000-000000000391")),
                PRODUCT_ID,
                new ProductVariantId(removedVariant1),
                sampleSnapshot("50.00"),
                1
            ),
            domainItem(
                new CartItemId(UUID.fromString("018f0000-0000-7000-8000-000000000392")),
                PRODUCT_ID,
                new ProductVariantId(removedVariant2),
                sampleSnapshot("60.00"),
                1
            )
        ))
        .build();

    cartRepository.insert(seedCart);
    testEntityManager.flush();
    testEntityManager.clear();

    assertThat(cartJpaRepository.findWithItemsById(CART_ID.getValue()))
        .get()
        .extracting(cart -> cart.getItems().size())
        .isEqualTo(3);

    Cart updatedCart = Cart.builder()
        .cartId(CART_ID)
        .userId(USER_ID)
        .items(List.of(domainItem(ITEM_ID, PRODUCT_ID, VARIANT_ID, sampleSnapshot("100.00"), 1)))
        .build();

    SqlStatementCounter.reset();
    cartRepository.update(updatedCart);
    testEntityManager.flush();

    assertThat(SqlStatementCounter.deleteCount()).isEqualTo(2);
    assertThat(cartJpaRepository.findWithItemsById(CART_ID.getValue()))
        .get()
        .satisfies(cart -> {
          assertThat(cart.getItems()).hasSize(1);
          assertThat(cart.getItems().get(0).getProductVariantId()).isEqualTo(VARIANT_ID.getValue());
        });
  }

  @Configuration
  @Import({
      OrderDataAccessConfig.class,
      CartRepositoryImpl.class,
      CartDataAccessMapper.class,
      CartAggregateMapper.class
  })
  static class Config {
  }
}
