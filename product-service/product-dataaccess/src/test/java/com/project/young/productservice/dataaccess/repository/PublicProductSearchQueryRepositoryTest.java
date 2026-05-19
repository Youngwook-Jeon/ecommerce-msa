package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.application.dto.condition.PublicProductSearchCondition;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.config.PublicProductSearchProperties;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.projection.PublicProductListProjection;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = PublicProductSearchQueryRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PublicProductSearchQueryRepositoryTest {

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
    private PublicProductSearchQueryRepository publicProductSearchQueryRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private CategoryEntity activeCategory;
    private CategoryEntity inactiveCategory;
    private CategoryEntity otherActiveCategory;

    @BeforeEach
    void setUp() {
        productJpaRepository.deleteAll();
        testEntityManager.flush();

        activeCategory = persistCategory("의류", CategoryStatusEntity.ACTIVE);
        inactiveCategory = persistCategory("전자제품", CategoryStatusEntity.INACTIVE);
        otherActiveCategory = persistCategory("잡화", CategoryStatusEntity.ACTIVE);

        persistProduct("와이드핏 데님", "와이드핏 데님 상세", "브랜드A",
                activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("30000"));
        persistProduct("스트레이트핏 데님", "스트레이트핏 데님 상세", "브랜드A",
                activeCategory, ProductStatusEntity.INACTIVE, new BigDecimal("25000"));
        persistProduct("게이밍 노트북", "게이밍 노트북 상세", "브랜드B",
                inactiveCategory, ProductStatusEntity.ACTIVE, new BigDecimal("1200000"));
        persistProduct("카테고리없음상품", "카테고리없음", "브랜드C",
                null, ProductStatusEntity.ACTIVE, new BigDecimal("5000"));
        persistProduct("다른 카테고리 상품", "설명", "브랜드D",
                otherActiveCategory, ProductStatusEntity.ACTIVE, new BigDecimal("15000"));

        testEntityManager.flush();
        testEntityManager.clear();
    }

    @Nested
    @DisplayName("가시성 필터")
    class VisibilityFilterTests {

        @Test
        @DisplayName("ACTIVE 상품 + ACTIVE 카테고리 + 요청 categoryId만 조회")
        void search_returnsOnlyActiveProductsInActiveCategory() {
            PublicProductSearchCondition condition =
                    new PublicProductSearchCondition(activeCategory.getId(), null, null, null, null);

            Page<PublicProductListProjection> page = publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.NEWEST, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent())
                    .extracting(PublicProductListProjection::name)
                    .containsExactly("와이드핏 데님");
            assertThat(page.getContent().getFirst().categoryId()).isEqualTo(activeCategory.getId());
        }

        @Test
        @DisplayName("다른 ACTIVE 카테고리 상품은 제외")
        void search_excludesOtherCategories() {
            PublicProductSearchCondition condition =
                    new PublicProductSearchCondition(otherActiveCategory.getId(), null, null, null, null);

            Page<PublicProductListProjection> page = publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.NEWEST, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().getFirst().name()).isEqualTo("다른 카테고리 상품");
        }
    }

    @Nested
    @DisplayName("퍼싯·키워드 필터")
    class FacetFilterTests {

        @BeforeEach
        void seedFacetProducts() {
            persistProduct("저가 데님", "저가", "브랜드A",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("9000"));
            persistProduct("고가 코튼", "코튼 소재", "브랜드E",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("80000"));
            testEntityManager.flush();
            testEntityManager.clear();
        }

        @Test
        @DisplayName("brand로 필터링")
        void search_filtersByBrand() {
            PublicProductSearchCondition condition = new PublicProductSearchCondition(
                    activeCategory.getId(), null, "브랜드A", null, null);

            Page<PublicProductListProjection> page = publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.NEWEST, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent())
                    .extracting(PublicProductListProjection::brand)
                    .containsOnly("브랜드A");
        }

        @Test
        @DisplayName("q는 이름·brand LIKE 검색 (기본 NAME_BRAND)")
        void search_filtersByKeywordOnNameOrBrand() {
            PublicProductSearchCondition condition = new PublicProductSearchCondition(
                    activeCategory.getId(), "데님", null, null, null);

            Page<PublicProductListProjection> page = publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.NEWEST, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent())
                    .extracting(PublicProductListProjection::name)
                    .containsExactlyInAnyOrder("와이드핏 데님", "저가 데님");
        }

        @Test
        @DisplayName("q가 brand에만 포함되어도 NAME_BRAND로 조회")
        void search_filtersByKeywordOnBrand() {
            PublicProductSearchCondition condition = new PublicProductSearchCondition(
                    activeCategory.getId(), "브랜드E", null, null, null);

            Page<PublicProductListProjection> page = publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.NEWEST, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().getFirst().name()).isEqualTo("고가 코튼");
        }

        @Test
        @DisplayName("설명에만 키워드가 있으면 기본 NAME_BRAND에서는 제외")
        void search_nameBrand_excludesDescriptionOnlyMatch() {
            persistProduct("무관한 이름", "여기만 데님 키워드", "브랜드Z",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("10000"));
            testEntityManager.flush();
            testEntityManager.clear();

            PublicProductSearchCondition condition = new PublicProductSearchCondition(
                    activeCategory.getId(), "데님", null, null, null);

            Page<PublicProductListProjection> page = publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.NEWEST, PageRequest.of(0, 50));

            assertThat(page.getContent())
                    .extracting(PublicProductListProjection::name)
                    .doesNotContain("무관한 이름");
        }

        @Test
        @DisplayName("NAME_DESCRIPTION_LEGACY는 설명 매칭 상품 포함")
        void search_legacyStrategy_includesDescriptionMatch() {
            persistProduct("무관한 이름", "여기만 데님 키워드", "브랜드Z",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("10000"));
            testEntityManager.flush();
            testEntityManager.clear();

            PublicProductSearchCondition condition = new PublicProductSearchCondition(
                    activeCategory.getId(), "데님", null, null, null);

            Page<PublicProductListProjection> page = publicProductSearchQueryRepository.search(
                    condition,
                    PublicProductSort.NEWEST,
                    PageRequest.of(0, 50),
                    PublicProductKeywordSearchStrategy.NAME_DESCRIPTION_LEGACY
            );

            assertThat(page.getContent())
                    .extracting(PublicProductListProjection::name)
                    .contains("무관한 이름");
        }

        @Test
        @DisplayName("minPrice·maxPrice로 basePrice 범위 필터")
        void search_filtersByPriceRange() {
            PublicProductSearchCondition condition = new PublicProductSearchCondition(
                    activeCategory.getId(),
                    null,
                    null,
                    new BigDecimal("10000"),
                    new BigDecimal("40000")
            );

            Page<PublicProductListProjection> page = publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.NEWEST, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().getFirst().name()).isEqualTo("와이드핏 데님");
            assertThat(page.getContent().getFirst().basePrice()).isEqualByComparingTo("30000");
        }
    }

    @Nested
    @DisplayName("정렬")
    class SortTests {

        @BeforeEach
        void seedSortProducts() {
            persistProduct("저가 상품", "d", "브랜드Z",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("1000"));
            persistProduct("고가 상품", "d", "브랜드Z",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("9000"));
            testEntityManager.flush();
            testEntityManager.clear();
        }

        @Test
        @DisplayName("PRICE_ASC — basePrice 오름차순")
        void search_sortsByPriceAsc() {
            PublicProductSearchCondition condition =
                    new PublicProductSearchCondition(activeCategory.getId(), null, null, null, null);

            Page<PublicProductListProjection> page = publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.PRICE_ASC, PageRequest.of(0, 10));

            assertThat(page.getContent())
                    .extracting(PublicProductListProjection::basePrice)
                    .isSortedAccordingTo(BigDecimal::compareTo);
        }

        @Test
        @DisplayName("PRICE_DESC — basePrice 내림차순")
        void search_sortsByPriceDesc() {
            PublicProductSearchCondition condition =
                    new PublicProductSearchCondition(activeCategory.getId(), null, null, null, null);

            List<BigDecimal> prices = publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.PRICE_DESC, PageRequest.of(0, 10)
            ).getContent().stream()
                    .map(PublicProductListProjection::basePrice)
                    .toList();

            assertThat(prices).isSortedAccordingTo((a, b) -> b.compareTo(a));
        }

        @Test
        @DisplayName("RELEVANCE + q — 이름 매칭 상품이 앞에 온다")
        void search_relevance_prefersNameMatch() {
            PublicProductSearchCondition condition = new PublicProductSearchCondition(
                    activeCategory.getId(), "데님", null, null, null);

            List<String> names = publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.RELEVANCE, PageRequest.of(0, 10)
            ).getContent().stream()
                    .map(PublicProductListProjection::name)
                    .toList();

            assertThat(names).isNotEmpty();
            assertThat(names.getFirst()).contains("데님");
        }
    }

    @Nested
    @DisplayName("페이지네이션")
    class PaginationTests {

        @Test
        @DisplayName("page·size에 맞게 슬라이스하고 totalElements를 반환")
        void search_paginatesResults() {
            persistProduct("추가 상품 A", "d", "브랜드X",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("11000"));
            persistProduct("추가 상품 B", "d", "브랜드X",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("12000"));
            testEntityManager.flush();

            PublicProductSearchCondition condition =
                    new PublicProductSearchCondition(activeCategory.getId(), null, null, null, null);
            Pageable pageable = PageRequest.of(0, 2);

            Page<PublicProductListProjection> page = publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.NEWEST, pageable);

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.getNumber()).isZero();
            assertThat(page.getSize()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class ValidationTests {

        @Test
        @DisplayName("condition이 null이면 IllegalArgumentException")
        void search_whenConditionNull_throws() {
            assertThatThrownBy(() -> publicProductSearchQueryRepository.search(
                    null, PublicProductSort.NEWEST, PageRequest.of(0, 10)
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PublicProductSearchCondition");
        }

        @Test
        @DisplayName("sort가 null이면 IllegalArgumentException")
        void search_whenSortNull_throws() {
            PublicProductSearchCondition condition =
                    new PublicProductSearchCondition(activeCategory.getId(), null, null, null, null);

            assertThatThrownBy(() -> publicProductSearchQueryRepository.search(
                    condition, null, PageRequest.of(0, 10)
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PublicProductSort");
        }
    }

    private CategoryEntity persistCategory(String name, CategoryStatusEntity status) {
        Instant now = Instant.now();
        return testEntityManager.persistAndFlush(CategoryEntity.builder()
                .name(name + "-" + UUID.randomUUID())
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private void persistProduct(
            String name,
            String description,
            String brand,
            CategoryEntity category,
            ProductStatusEntity status,
            BigDecimal basePrice
    ) {
        Instant now = Instant.now();
        testEntityManager.persist(ProductEntity.builder()
                .id(UUID.randomUUID())
                .category(category)
                .name(name)
                .description(description)
                .basePrice(basePrice)
                .status(status)
                .conditionType(ConditionTypeEntity.NEW)
                .brand(brand)
                .mainImageUrl("https://example.com/" + UUID.randomUUID() + ".jpg")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Configuration
    @Import({ProductDataAccessConfig.class, PublicProductSearchQueryRepository.class})
    @org.springframework.boot.context.properties.EnableConfigurationProperties(PublicProductSearchProperties.class)
    static class Config {
    }
}
