package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.entity.VariantOptionValueEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
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
    @DisplayName("findAggregateById 테스트")
    class FindAggregateByIdTests {

        @Test
        @DisplayName("상품 aggregate 조회 시 하위 컬렉션까지 로딩된다")
        void findAggregateById_LoadsSubCollections() {
            CategoryEntity category = testEntityManager.persistAndFlush(
                    createCategory("의류", CategoryStatusEntity.ACTIVE)
            );

            OptionGroupEntity globalOptionGroup = OptionGroupEntity.builder()
                    .id(UUID.randomUUID())
                    .name("size")
                    .displayName("사이즈")
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            testEntityManager.persist(globalOptionGroup);

            OptionValueEntity globalOptionValue = OptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .optionGroup(globalOptionGroup)
                    .value("M")
                    .displayName("미디움")
                    .sortOrder(1)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            testEntityManager.persistAndFlush(globalOptionValue);

            UUID referencedOptionGroupId = globalOptionGroup.getId();
            UUID referencedOptionValueId = globalOptionValue.getId();
            UUID selectedProductOptionValueId = UUID.randomUUID();

            ProductEntity product = createProduct("와이드핏 데님", category, ProductStatusEntity.ACTIVE);

            ProductOptionGroupEntity optionGroup = ProductOptionGroupEntity.builder()
                    .id(UUID.randomUUID())
                    .optionGroupId(referencedOptionGroupId)
                    .stepOrder(1)
                    .isRequired(true)
                    .build();

            ProductOptionValueEntity optionValue = ProductOptionValueEntity.builder()
                    .id(selectedProductOptionValueId)
                    .optionValueId(referencedOptionValueId)
                    .priceDelta(new BigDecimal("1500"))
                    .isDefault(true)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            optionGroup.addOptionValue(optionValue);
            product.addOptionGroup(optionGroup);

            ProductVariantEntity variant = ProductVariantEntity.builder()
                    .id(UUID.randomUUID())
                    .sku("SKU-AGG-001")
                    .stockQuantity(10)
                    .status(ProductStatusEntity.ACTIVE)
                    .calculatedPrice(new BigDecimal("11500"))
                    .build();

            VariantOptionValueEntity selected = VariantOptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .productOptionValueId(selectedProductOptionValueId)
                    .build();
            variant.addSelectedOptionValue(selected);
            product.addVariant(variant);

            ProductEntity saved = testEntityManager.persistAndFlush(product);
            testEntityManager.clear();

            Optional<ProductEntity> found = productJpaRepository.findAggregateById(saved.getId());

            assertThat(found).isPresent();
            ProductEntity aggregate = found.get();
            assertThat(Hibernate.isInitialized(aggregate.getOptionGroups())).isTrue();
            assertThat(Hibernate.isInitialized(aggregate.getVariants())).isTrue();
            assertThat(aggregate.getOptionGroups()).hasSize(1);
            assertThat(aggregate.getVariants()).hasSize(1);

            ProductOptionGroupEntity loadedGroup = aggregate.getOptionGroups().iterator().next();
            ProductVariantEntity loadedVariant = aggregate.getVariants().iterator().next();

            assertThat(Hibernate.isInitialized(loadedGroup.getOptionValues())).isTrue();
            assertThat(Hibernate.isInitialized(loadedVariant.getSelectedOptionValues())).isTrue();
            assertThat(loadedGroup.getOptionValues()).hasSize(1);
            assertThat(loadedVariant.getSelectedOptionValues()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("existsVariantSku 테스트")
    class ExistsVariantSkuTests {

        @Test
        @DisplayName("등록된 variant SKU 존재 여부를 반환한다")
        void existsVariantSku_ReturnsTrueWhenExists() {
            CategoryEntity category = testEntityManager.persistAndFlush(
                    createCategory("의류", CategoryStatusEntity.ACTIVE)
            );

            ProductEntity product = createProduct("데님", category, ProductStatusEntity.ACTIVE);
            ProductVariantEntity variant = ProductVariantEntity.builder()
                    .id(UUID.randomUUID())
                    .sku("SKU-EXIST-001")
                    .stockQuantity(3)
                    .status(ProductStatusEntity.ACTIVE)
                    .calculatedPrice(new BigDecimal("10000"))
                    .build();
            product.addVariant(variant);
            testEntityManager.persistAndFlush(product);

            boolean exists = productJpaRepository.existsVariantSku("SKU-EXIST-001");
            boolean notExists = productJpaRepository.existsVariantSku("SKU-NOT-FOUND");

            assertThat(exists).isTrue();
            assertThat(notExists).isFalse();
        }
    }

    @Nested
    @DisplayName("findAllVisible 테스트")
    class FindAllVisibleTests {

        @Test
        @DisplayName("ACTIVE 상품 + ACTIVE 카테고리만 전체 조회된다")
        void findAllVisible_ReturnsOnlyActiveProductsInActiveCategories() {
            // Given
            CategoryEntity activeCategory = createCategory("의류", CategoryStatusEntity.ACTIVE);
            CategoryEntity inactiveCategory = createCategory("전자제품", CategoryStatusEntity.INACTIVE);

            CategoryEntity savedActiveCategory = testEntityManager.persistAndFlush(activeCategory);
            CategoryEntity savedInactiveCategory = testEntityManager.persistAndFlush(inactiveCategory);

            // ACTIVE product in ACTIVE category
            ProductEntity activeInActiveCategory = createProduct(
                    "와이드핏 데님",
                    savedActiveCategory,
                    ProductStatusEntity.ACTIVE
            );

            // INACTIVE product in ACTIVE category
            ProductEntity inactiveInActiveCategory = createProduct(
                    "스트레이트핏 데님",
                    savedActiveCategory,
                    ProductStatusEntity.INACTIVE
            );

            // ACTIVE product in INACTIVE category
            ProductEntity activeInInactiveCategory = createProduct(
                    "게이밍 노트북",
                    savedInactiveCategory,
                    ProductStatusEntity.ACTIVE
            );

            // ACTIVE product without category (JOIN 이므로 제외 대상)
            ProductEntity activeWithoutCategory = createProduct(
                    "카테고리없음상품",
                    null,
                    ProductStatusEntity.ACTIVE
            );

            testEntityManager.persist(activeInActiveCategory);
            testEntityManager.persist(inactiveInActiveCategory);
            testEntityManager.persist(activeInInactiveCategory);
            testEntityManager.persist(activeWithoutCategory);
            testEntityManager.flush();

            // When
            List<ProductEntity> result = productJpaRepository.findAllVisible(
                    ProductStatusEntity.ACTIVE,
                    CategoryStatusEntity.ACTIVE
            );

            // Then
            assertThat(result)
                    .hasSize(1)
                    .first()
                    .extracting(ProductEntity::getName)
                    .isEqualTo("와이드핏 데님");
        }
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

    @Nested
    @DisplayName("findVisibleDetailById 테스트")
    class FindVisibleDetailByIdTests {

        @Test
        @DisplayName("visible 상품 상세 조회 시 하위 컬렉션까지 로딩된다")
        void findVisibleDetailById_LoadsSubCollections() {
            CategoryEntity category = testEntityManager.persistAndFlush(
                    createCategory("의류", CategoryStatusEntity.ACTIVE)
            );

            OptionGroupEntity globalOptionGroup = OptionGroupEntity.builder()
                    .id(UUID.randomUUID())
                    .name("color")
                    .displayName("색상")
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            testEntityManager.persist(globalOptionGroup);

            OptionValueEntity globalOptionValue = OptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .optionGroup(globalOptionGroup)
                    .value("BLACK")
                    .displayName("블랙")
                    .sortOrder(1)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            testEntityManager.persistAndFlush(globalOptionValue);

            ProductEntity product = createProduct("오버핏 티셔츠", category, ProductStatusEntity.ACTIVE);

            ProductOptionGroupEntity optionGroup = ProductOptionGroupEntity.builder()
                    .id(UUID.randomUUID())
                    .optionGroupId(globalOptionGroup.getId())
                    .stepOrder(1)
                    .isRequired(true)
                    .build();
            ProductOptionValueEntity optionValue = ProductOptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .optionValueId(globalOptionValue.getId())
                    .priceDelta(new BigDecimal("2000"))
                    .isDefault(true)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            optionGroup.addOptionValue(optionValue);
            product.addOptionGroup(optionGroup);

            ProductVariantEntity variant = ProductVariantEntity.builder()
                    .id(UUID.randomUUID())
                    .sku("SKU-VISIBLE-DETAIL")
                    .stockQuantity(7)
                    .status(ProductStatusEntity.ACTIVE)
                    .calculatedPrice(new BigDecimal("12000"))
                    .build();
            VariantOptionValueEntity selected = VariantOptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .productOptionValueId(optionValue.getId())
                    .build();
            variant.addSelectedOptionValue(selected);
            product.addVariant(variant);

            ProductEntity saved = testEntityManager.persistAndFlush(product);
            testEntityManager.clear();

            Optional<ProductEntity> found = productJpaRepository.findVisibleDetailById(
                    saved.getId(),
                    ProductStatusEntity.ACTIVE,
                    CategoryStatusEntity.ACTIVE
            );

            assertThat(found).isPresent();
            ProductEntity detail = found.get();
            assertThat(Hibernate.isInitialized(detail.getOptionGroups())).isTrue();
            assertThat(Hibernate.isInitialized(detail.getVariants())).isTrue();
            assertThat(detail.getOptionGroups()).hasSize(1);
            assertThat(detail.getVariants()).hasSize(1);

            ProductOptionGroupEntity loadedGroup = detail.getOptionGroups().iterator().next();
            ProductVariantEntity loadedVariant = detail.getVariants().iterator().next();
            assertThat(Hibernate.isInitialized(loadedGroup.getOptionValues())).isTrue();
            assertThat(Hibernate.isInitialized(loadedVariant.getSelectedOptionValues())).isTrue();
            assertThat(loadedGroup.getOptionValues()).hasSize(1);
            assertThat(loadedVariant.getSelectedOptionValues()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("상세 조회 성능 비교 테스트")
    class VisibleDetailQueryComparisonTests {

        @Test
        @DisplayName("단일 조회 방식과 분할 조회 방식의 소요 시간을 비교 출력한다")
        void compareSingleJoinAndSplitQueries() {
            CategoryEntity category = testEntityManager.persistAndFlush(
                    createCategory("의류", CategoryStatusEntity.ACTIVE)
            );

            ProductEntity product = createProduct("성능비교용 상품", category, ProductStatusEntity.ACTIVE);

            for (int groupIndex = 0; groupIndex < 4; groupIndex++) {
                OptionGroupEntity globalOptionGroup = OptionGroupEntity.builder()
                        .id(UUID.randomUUID())
                        .name("opt-group-" + groupIndex)
                        .displayName("옵션그룹-" + groupIndex)
                        .status(OptionStatusEntity.ACTIVE)
                        .build();
                testEntityManager.persist(globalOptionGroup);

                ProductOptionGroupEntity pog = ProductOptionGroupEntity.builder()
                        .id(UUID.randomUUID())
                        .optionGroupId(globalOptionGroup.getId())
                        .stepOrder(groupIndex + 1)
                        .isRequired(true)
                        .build();

                for (int valueIndex = 0; valueIndex < 4; valueIndex++) {
                    OptionValueEntity globalValue = OptionValueEntity.builder()
                            .id(UUID.randomUUID())
                            .optionGroup(globalOptionGroup)
                            .value("VAL-" + groupIndex + "-" + valueIndex)
                            .displayName("값-" + groupIndex + "-" + valueIndex)
                            .sortOrder(valueIndex + 1)
                            .status(OptionStatusEntity.ACTIVE)
                            .build();
                    testEntityManager.persist(globalValue);

                    ProductOptionValueEntity pov = ProductOptionValueEntity.builder()
                            .id(UUID.randomUUID())
                            .optionValueId(globalValue.getId())
                            .priceDelta(new BigDecimal("500"))
                            .isDefault(valueIndex == 0)
                            .status(OptionStatusEntity.ACTIVE)
                            .build();
                    pog.addOptionValue(pov);
                }
                product.addOptionGroup(pog);
            }

            UUID firstProductOptionValueId = product.getOptionGroups().iterator().next()
                    .getOptionValues().iterator().next().getId();

            for (int variantIndex = 0; variantIndex < 30; variantIndex++) {
                ProductVariantEntity variant = ProductVariantEntity.builder()
                        .id(UUID.randomUUID())
                        .sku("SKU-BENCH-" + variantIndex)
                        .stockQuantity(10 + variantIndex)
                        .status(ProductStatusEntity.ACTIVE)
                        .calculatedPrice(new BigDecimal("15000"))
                        .build();
                variant.addSelectedOptionValue(VariantOptionValueEntity.builder()
                        .id(UUID.randomUUID())
                        .productOptionValueId(firstProductOptionValueId)
                        .build());
                product.addVariant(variant);
            }

            ProductEntity saved = testEntityManager.persistAndFlush(product);
            testEntityManager.clear();

            final int rounds = 20;
            long singleTotalNanos = 0L;
            long splitTotalNanos = 0L;

            for (int i = 0; i < rounds; i++) {
                testEntityManager.clear();
                long start = System.nanoTime();
                ProductEntity loaded = productJpaRepository.findVisibleDetailById(
                        saved.getId(), ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE
                ).orElseThrow();
                loaded.getOptionGroups().size();
                loaded.getVariants().size();
                singleTotalNanos += (System.nanoTime() - start);
            }

            for (int i = 0; i < rounds; i++) {
                testEntityManager.clear();
                long start = System.nanoTime();
                ProductEntity loaded = productJpaRepository.findVisibleDetailWithOptionsById(
                        saved.getId(), ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE
                ).orElseThrow();
                productJpaRepository.findVisibleDetailWithVariantsById(
                        saved.getId(), ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE
                ).orElseThrow();
                loaded.getOptionGroups().size();
                loaded.getVariants().size();
                splitTotalNanos += (System.nanoTime() - start);
            }

            double singleAvgMs = singleTotalNanos / 1_000_000.0 / rounds;
            double splitAvgMs = splitTotalNanos / 1_000_000.0 / rounds;
            // Visible detail query benchmark (rounds=20): single=9.309ms, split=4.800ms (in M3 Max processor)
            System.out.printf(
                    "Visible detail query benchmark (rounds=%d): single=%.3fms, split=%.3fms%n",
                    rounds, singleAvgMs, splitAvgMs
            );

            assertThat(singleAvgMs).isGreaterThan(0.0);
            assertThat(splitAvgMs).isGreaterThan(0.0);
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

