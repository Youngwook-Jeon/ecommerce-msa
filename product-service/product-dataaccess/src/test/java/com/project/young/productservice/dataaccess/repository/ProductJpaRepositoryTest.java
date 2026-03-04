package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = ProductJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductJpaRepositoryTest {

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
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void setUp() {
        productJpaRepository.deleteAll();
        testEntityManager.flush();
    }

    @Nested
    @DisplayName("findVisibleByCategoryId 테스트")
    class FindVisibleByCategoryIdTests {

        @Test
        @DisplayName("ACTIVE 상품 + ACTIVE 카테고리만 조회된다")
        void findVisibleByCategoryId_ReturnsOnlyActiveProductsInActiveCategory() {
            // Given
            CategoryEntity activeCategory = createCategory("의류", CategoryStatusEntity.ACTIVE);
            CategoryEntity inactiveCategory = createCategory("전자제품", CategoryStatusEntity.INACTIVE);

            CategoryEntity savedActiveCategory = testEntityManager.persistAndFlush(activeCategory);
            CategoryEntity savedInactiveCategory = testEntityManager.persistAndFlush(inactiveCategory);

            // ACTIVE product in ACTIVE category
            ProductEntity visibleProduct = createProduct(
                    "와이드핏 데님",
                    savedActiveCategory,
                    ProductStatusEntity.ACTIVE
            );

            // INACTIVE product in ACTIVE category
            ProductEntity inactiveProductInActiveCategory = createProduct(
                    "스트레이트핏 데님",
                    savedActiveCategory,
                    ProductStatusEntity.INACTIVE
            );

            // ACTIVE product in INACTIVE category
            ProductEntity activeProductInInactiveCategory = createProduct(
                    "노트북",
                    savedInactiveCategory,
                    ProductStatusEntity.ACTIVE
            );

            testEntityManager.persist(visibleProduct);
            testEntityManager.persist(inactiveProductInActiveCategory);
            testEntityManager.persist(activeProductInInactiveCategory);
            testEntityManager.flush();

            // When
            List<ProductEntity> result = productJpaRepository.findVisibleByCategoryId(
                    savedActiveCategory.getId(),
                    ProductStatusEntity.ACTIVE,
                    CategoryStatusEntity.ACTIVE
            );

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getName()).isEqualTo("와이드핏 데님");
        }
    }

    @Nested
    @DisplayName("findVisibleById 테스트")
    class FindVisibleByIdTests {

        @Test
        @DisplayName("카테고리가 ACTIVE이거나 없고, 상품이 ACTIVE일 때만 조회된다")
        void findVisibleById_RespectsProductAndCategoryStatus() {
            // Given
            CategoryEntity activeCategory = createCategory("의류", CategoryStatusEntity.ACTIVE);
            CategoryEntity inactiveCategory = createCategory("전자제품", CategoryStatusEntity.INACTIVE);

            CategoryEntity savedActiveCategory = testEntityManager.persistAndFlush(activeCategory);
            CategoryEntity savedInactiveCategory = testEntityManager.persistAndFlush(inactiveCategory);

            ProductEntity visibleWithCategory = createProduct(
                    "와이드핏 데님",
                    savedActiveCategory,
                    ProductStatusEntity.ACTIVE
            );

            ProductEntity invisibleWithInactiveCategory = createProduct(
                    "노트북",
                    savedInactiveCategory,
                    ProductStatusEntity.ACTIVE
            );

            ProductEntity invisibleInactiveProduct = createProduct(
                    "스트레이트핏 데님",
                    savedActiveCategory,
                    ProductStatusEntity.INACTIVE
            );

            ProductEntity visibleWithoutCategory = createProduct(
                    "카테고리없음상품",
                    null,
                    ProductStatusEntity.ACTIVE
            );

            visibleWithCategory = testEntityManager.persistAndFlush(visibleWithCategory);
            invisibleWithInactiveCategory = testEntityManager.persistAndFlush(invisibleWithInactiveCategory);
            invisibleInactiveProduct = testEntityManager.persistAndFlush(invisibleInactiveProduct);
            visibleWithoutCategory = testEntityManager.persistAndFlush(visibleWithoutCategory);

            // When
            Optional<ProductEntity> foundVisibleWithCategory = productJpaRepository.findVisibleById(
                    visibleWithCategory.getId(),
                    ProductStatusEntity.ACTIVE,
                    CategoryStatusEntity.ACTIVE
            );

            Optional<ProductEntity> foundInvisibleWithInactiveCategory = productJpaRepository.findVisibleById(
                    invisibleWithInactiveCategory.getId(),
                    ProductStatusEntity.ACTIVE,
                    CategoryStatusEntity.ACTIVE
            );

            Optional<ProductEntity> foundInactiveProduct = productJpaRepository.findVisibleById(
                    invisibleInactiveProduct.getId(),
                    ProductStatusEntity.ACTIVE,
                    CategoryStatusEntity.ACTIVE
            );

            Optional<ProductEntity> foundVisibleWithoutCategory = productJpaRepository.findVisibleById(
                    visibleWithoutCategory.getId(),
                    ProductStatusEntity.ACTIVE,
                    CategoryStatusEntity.ACTIVE
            );

            // Then
            assertThat(foundVisibleWithCategory).isPresent();
            assertThat(foundVisibleWithCategory.get().getName()).isEqualTo("와이드핏 데님");

            assertThat(foundInvisibleWithInactiveCategory).isEmpty();
            assertThat(foundInactiveProduct).isEmpty();

            assertThat(foundVisibleWithoutCategory).isPresent();
            assertThat(foundVisibleWithoutCategory.get().getName()).isEqualTo("카테고리없음상품");
        }
    }

    private CategoryEntity createCategory(String name, CategoryStatusEntity status) {
        Instant now = Instant.now();
        return CategoryEntity.builder()
                .name(name)
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private ProductEntity createProduct(String name, CategoryEntity category, ProductStatusEntity status) {
        Instant now = Instant.now();
        return ProductEntity.builder()
                .id(UUID.randomUUID())
                .category(category)
                .name(name)
                .description(name + " 상세 설명입니다.")
                .basePrice(new BigDecimal("10000"))
                .status(status)
                .conditionType(ConditionTypeEntity.NEW)
                .brand("브랜드")
                .mainImageUrl("https://example.com/image.jpg")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Configuration
    @Import(ProductDataAccessConfig.class)
    static class Config {
    }
}

