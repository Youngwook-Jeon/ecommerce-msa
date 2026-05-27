package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.application.dto.query.PublicProductFacetQuery;
import com.project.young.productservice.application.dto.query.PublicProductFacetType;
import com.project.young.productservice.application.dto.result.PublicProductBrandFacetValueResult;
import com.project.young.productservice.application.dto.result.PublicProductFacetResult;
import com.project.young.productservice.application.dto.result.PublicProductPriceFacetBucketResult;
import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.config.PublicProductSearchProperties;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = PublicProductFacetQueryRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PublicProductFacetQueryRepositoryTest {

    @Container
    @SuppressWarnings("resource")
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
    private PublicProductFacetQueryRepository publicProductFacetQueryRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private CategoryEntity activeCategory;

    @BeforeEach
    void setUp() {
        productJpaRepository.deleteAll();
        testEntityManager.flush();

        activeCategory = persistCategory("의류", CategoryStatusEntity.ACTIVE);

        persistProduct("와이드핏 데님", "와이드핏 데님 상세", "브랜드A",
                activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("30000"));

        testEntityManager.flush();
        testEntityManager.clear();
    }

    @Nested
    @DisplayName("totalMatching")
    class TotalMatchingTests {

        @Test
        @DisplayName("ACTIVE 상품만 ACTIVE 카테고리 기준으로 집계한다")
        void getFacets_countsActiveProductsInCategory() {
            persistProduct("비활성", "설명", "브랜드Z",
                    activeCategory, ProductStatusEntity.INACTIVE, new BigDecimal("100"));
            testEntityManager.flush();
            testEntityManager.clear();

            PublicProductFacetResult result = publicProductFacetQueryRepository.getFacets(
                    baseQuery(List.of(PublicProductFacetType.BRAND, PublicProductFacetType.PRICE)));

            assertThat(result.totalMatching()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("brand facet")
    class BrandFacetTests {

        @Test
        @DisplayName("브랜드별 건수를 반환한다")
        void getFacets_groupsByBrand() {
            persistProduct("코튼 티", "설명", "브랜드B",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("20000"));
            testEntityManager.flush();
            testEntityManager.clear();

            PublicProductFacetResult result = publicProductFacetQueryRepository.getFacets(
                    baseQuery(List.of(PublicProductFacetType.BRAND, PublicProductFacetType.PRICE)));

            assertThat(result.brands())
                    .extracting(PublicProductBrandFacetValueResult::value, PublicProductBrandFacetValueResult::count)
                    .containsExactlyInAnyOrder(
                            tuple("브랜드A", 1L),
                            tuple("브랜드B", 1L)
                    );
            assertThat(result.totalMatching()).isEqualTo(2L);
        }

        @Test
        @DisplayName("선택된 브랜드 필터는 totalMatching에만 적용하고 브랜드 퍼싯은 disjunctive로 집계한다")
        void getFacets_brandFilter_excludedFromBrandFacetCounts() {
            persistProduct("운동화", "설명", "BrandM",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("160"));
            persistProduct("샌들", "설명", "BrandN",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("150"));
            testEntityManager.flush();
            testEntityManager.clear();

            PublicProductFacetQuery query = new PublicProductFacetQuery(
                    activeCategory.getId(),
                    null,
                    List.of("BrandN"),
                    null,
                    null,
                    List.of(PublicProductFacetType.BRAND, PublicProductFacetType.PRICE)
            );

            PublicProductFacetResult result = publicProductFacetQueryRepository.getFacets(query);

            assertThat(result.totalMatching()).isEqualTo(1L);
            assertThat(result.brands())
                    .extracting(PublicProductBrandFacetValueResult::value, PublicProductBrandFacetValueResult::count)
                    .containsExactlyInAnyOrder(
                            tuple("브랜드A", 1L),
                            tuple("BrandM", 1L),
                            tuple("BrandN", 1L)
                    );
            assertThat(result.brands())
                    .filteredOn(PublicProductBrandFacetValueResult::selected)
                    .extracting(PublicProductBrandFacetValueResult::value)
                    .containsExactly("BrandN");
        }
    }

    @Nested
    @DisplayName("facet 타입 선택")
    class FacetTypeSelectionTests {

        @Test
        @DisplayName("facet=brand 만 요청하면 priceBuckets는 비어 있다")
        void getFacets_brandOnly_omitsPriceBuckets() {
            PublicProductFacetResult result = publicProductFacetQueryRepository.getFacets(
                    baseQuery(List.of(PublicProductFacetType.BRAND)));

            assertThat(result.brands()).isNotEmpty();
            assertThat(result.priceBuckets()).isEmpty();
        }

        @Test
        @DisplayName("facet=price 만 요청하면 brands는 비어 있다")
        void getFacets_priceOnly_omitsBrands() {
            PublicProductFacetResult result = publicProductFacetQueryRepository.getFacets(
                    baseQuery(List.of(PublicProductFacetType.PRICE)));

            assertThat(result.brands()).isEmpty();
            assertThat(result.priceBuckets()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("price facet")
    class PriceFacetTests {

        @Test
        @DisplayName("가격 버킷별 건수를 반환한다 (base_price 기준)")
        void getFacets_priceBuckets_matchRanges() {
            persistProduct("저가", "설명", "브랜드L",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("15"));
            persistProduct("중가", "설명", "브랜드M",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("75"));
            testEntityManager.flush();
            testEntityManager.clear();

            PublicProductFacetResult result = publicProductFacetQueryRepository.getFacets(
                    baseQuery(List.of(PublicProductFacetType.PRICE)));

            assertThat(result.priceBuckets())
                    .extracting(PublicProductPriceFacetBucketResult::id, PublicProductPriceFacetBucketResult::count)
                    .contains(
                            tuple("under_25", 1L),
                            tuple("25_50", 0L),
                            tuple("50_100", 1L),
                            tuple("100_200", 0L),
                            tuple("200_plus", 1L)
                    );
        }
    }

    @Nested
    @DisplayName("키워드 q")
    class KeywordFacetTests {

        @Test
        @DisplayName("q가 있으면 목록과 동일한 NAME_BRAND 키워드 조건으로 퍼싯을 제한한다")
        void getFacets_withKeyword_filtersFacets() {
            persistProduct("무관한 이름", "설명만 데님", "브랜드X",
                    activeCategory, ProductStatusEntity.ACTIVE, new BigDecimal("10000"));
            testEntityManager.flush();
            testEntityManager.clear();

            PublicProductFacetQuery query = new PublicProductFacetQuery(
                    activeCategory.getId(),
                    "데님",
                    List.of(),
                    null,
                    null,
                    List.of(PublicProductFacetType.BRAND, PublicProductFacetType.PRICE)
            );

            PublicProductFacetResult result = publicProductFacetQueryRepository.getFacets(query);

            assertThat(result.totalMatching()).isEqualTo(1L);
            assertThat(result.brands())
                    .extracting(PublicProductBrandFacetValueResult::value)
                    .containsExactly("브랜드A");
        }
    }

    private PublicProductFacetQuery baseQuery(List<PublicProductFacetType> facets) {
        return new PublicProductFacetQuery(
                activeCategory.getId(),
                null,
                List.of(),
                null,
                null,
                facets
        );
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
    @Import({ProductDataAccessConfig.class, PublicProductFacetQueryRepository.class})
    @org.springframework.boot.context.properties.EnableConfigurationProperties(PublicProductSearchProperties.class)
    static class Config {
    }
}
