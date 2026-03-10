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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = AdminProductJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminProductJpaRepositoryTest {

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
    private AdminProductJpaRepository adminProductJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private CategoryEntity activeCategory;
    private CategoryEntity inactiveCategory;

    @BeforeEach
    void setUp() {
        adminProductJpaRepository.deleteAll();
        testEntityManager.flush();

        activeCategory = testEntityManager.persistAndFlush(createCategory("의류", CategoryStatusEntity.ACTIVE));
        inactiveCategory = testEntityManager.persistAndFlush(createCategory("전자제품", CategoryStatusEntity.INACTIVE));

        testEntityManager.persist(createProduct("와이드핏 데님", "와이드핏 데님 상세 설명입니다.", "브랜드A",
                activeCategory, ProductStatusEntity.ACTIVE));

        testEntityManager.persist(createProduct("스트레이트핏 데님", "스트레이트핏 데님 상세 설명입니다.", "브랜드A",
                activeCategory, ProductStatusEntity.INACTIVE));

        testEntityManager.persist(createProduct("게이밍 노트북", "게이밍 노트북 상세 설명입니다.", "브랜드B",
                inactiveCategory, ProductStatusEntity.ACTIVE));

        testEntityManager.persist(createProduct("카테고리없음상품", "카테고리없음상품 설명입니다.", "브랜드C",
                null, ProductStatusEntity.ACTIVE));

        testEntityManager.flush();
    }

    @Nested
    @DisplayName("기본 검색 조건 테스트")
    class BasicSearchTests {

        @Test
        @DisplayName("status=ACTIVE 이면 ACTIVE 상품만 조회된다")
        void searchAdminProducts_FiltersByStatus() {
            Pageable pageable = PageRequest.of(0, 10);

            Page<ProductEntity> page = adminProductJpaRepository.searchAdminProducts(
                    false,
                    null,
                    true,
                    true,
                    ProductStatusEntity.ACTIVE,
                    false,
                    null,
                    false,
                    null,
                    pageable
            );

            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getContent())
                    .extracting(ProductEntity::getStatus)
                    .allMatch(status -> status == ProductStatusEntity.ACTIVE);
        }

        @Test
        @DisplayName("categoryId로 필터링할 수 있다")
        void searchAdminProducts_FiltersByCategoryId() {
            Pageable pageable = PageRequest.of(0, 10);

            Page<ProductEntity> page = adminProductJpaRepository.searchAdminProducts(
                    true,
                    activeCategory.getId(),
                    true,
                    false,
                    null,
                    false,
                    null,
                    false,
                    null,
                    pageable
            );

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent())
                    .extracting(ProductEntity::getName)
                    .containsExactlyInAnyOrder("와이드핏 데님", "스트레이트핏 데님");
        }
    }

    @Nested
    @DisplayName("includeOrphans 플래그 테스트")
    class IncludeOrphansTests {

        @Test
        @DisplayName("includeOrphans=true 이면 카테고리 없는 상품도 포함된다")
        void searchAdminProducts_IncludeOrphansTrue_IncludesCategoryLessProducts() {
            Pageable pageable = PageRequest.of(0, 10);

            Page<ProductEntity> page = adminProductJpaRepository.searchAdminProducts(
                    false,
                    null,
                    true,
                    false,
                    null,
                    false,
                    null,
                    false,
                    null,
                    pageable
            );

            assertThat(page.getTotalElements()).isEqualTo(4);
            assertThat(page.getContent())
                    .extracting(ProductEntity::getName)
                    .contains("카테고리없음상품");
        }

        @Test
        @DisplayName("includeOrphans=false 이면 카테고리 없는 상품은 제외된다")
        void searchAdminProducts_IncludeOrphansFalse_ExcludesCategoryLessProducts() {
            Pageable pageable = PageRequest.of(0, 10);

            Page<ProductEntity> page = adminProductJpaRepository.searchAdminProducts(
                    false,
                    null,
                    false,
                    false,
                    null,
                    false,
                    null,
                    false,
                    null,
                    pageable
            );

            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getContent())
                    .extracting(ProductEntity::getName)
                    .doesNotContain("카테고리없음상품");
        }
    }

    @Nested
    @DisplayName("브랜드/키워드 필터 테스트")
    class BrandAndKeywordTests {

        @Test
        @DisplayName("brand로 필터링할 수 있다")
        void searchAdminProducts_FiltersByBrand() {
            Pageable pageable = PageRequest.of(0, 10);

            Page<ProductEntity> page = adminProductJpaRepository.searchAdminProducts(
                    false,
                    null,
                    true,
                    false,
                    null,
                    true,
                    "브랜드A",
                    false,
                    null,
                    pageable
            );

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent())
                    .extracting(ProductEntity::getBrand)
                    .allMatch("브랜드A"::equals);
        }

        @Test
        @DisplayName("keyword는 이름/설명에 대해 LIKE 검색을 수행한다")
        void searchAdminProducts_FiltersByKeyword() {
            Pageable pageable = PageRequest.of(0, 10);

            Page<ProductEntity> page = adminProductJpaRepository.searchAdminProducts(
                    false,
                    null,
                    true,
                    false,
                    null,
                    false,
                    null,
                    true,
                    "데님",
                    pageable
            );

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent())
                    .extracting(ProductEntity::getName)
                    .containsExactlyInAnyOrder("와이드핏 데님", "스트레이트핏 데님");
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

    private ProductEntity createProduct(String name,
                                        String description,
                                        String brand,
                                        CategoryEntity category,
                                        ProductStatusEntity status) {
        Instant now = Instant.now();
        return ProductEntity.builder()
                .id(UUID.randomUUID())
                .category(category)
                .name(name)
                .description(description)
                .basePrice(new BigDecimal("10000"))
                .status(status)
                .conditionType(ConditionTypeEntity.NEW)
                .brand(brand)
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