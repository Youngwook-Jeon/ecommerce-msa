package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = ProductVariantJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings("resource")
class ProductVariantJpaRepositoryTest {

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
    private ProductVariantJpaRepository productVariantJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private JpaRepositoryTestFixtures.CompositionGraph graph;

    @BeforeEach
    void setUp() {
        JpaRepositoryTestFixtures.truncateCompositionTables(testEntityManager);
        graph = JpaRepositoryTestFixtures.persistColorSizeProduct(testEntityManager);
    }

    @Test
    @DisplayName("findAllIdsByProductId: 상품의 variant id 목록을 조회한다")
    void findAllIdsByProductId_returnsVariantIds() {
        List<java.util.UUID> variantIds = productVariantJpaRepository.findAllIdsByProductId(graph.productId());

        assertThat(variantIds).containsExactly(graph.variantId());
    }

    @Test
    @DisplayName("findAllIdsByProductOptionValueId: POV를 선택한 variant id 목록을 조회한다")
    void findAllIdsByProductOptionValueId_returnsMatchingVariantIds() {
        assertThat(productVariantJpaRepository.findAllIdsByProductOptionValueId(graph.redPovId()))
                .containsExactly(graph.variantId());
        assertThat(productVariantJpaRepository.findAllIdsByProductOptionValueId(graph.largePovId()))
                .containsExactly(graph.variantId());
        assertThat(productVariantJpaRepository.findAllIdsByProductOptionValueId(graph.bluePovId()))
                .isEmpty();
    }

    @Test
    @DisplayName("findAllByProductIdWithSelectedOptionValues: variant와 selectedOptionValues를 fetch join한다")
    void findAllByProductIdWithSelectedOptionValues_fetchesSelectedOptionValues() {
        List<ProductVariantEntity> variants =
                productVariantJpaRepository.findAllByProductIdWithSelectedOptionValues(graph.productId());

        assertThat(variants).hasSize(1);
        assertThat(variants.getFirst().getSelectedOptionValues()).hasSize(2);
    }

    @Test
    @DisplayName("findByIdWithSelectedOptionValuesAndProduct: variant, product, option values를 함께 조회한다")
    void findByIdWithSelectedOptionValuesAndProduct_fetchesAssociations() {
        ProductVariantEntity variant = productVariantJpaRepository
                .findByIdWithSelectedOptionValuesAndProduct(graph.variantId())
                .orElseThrow();

        assertThat(variant.getProduct().getId()).isEqualTo(graph.productId());
        assertThat(variant.getSelectedOptionValues()).hasSize(2);
    }

    @Test
    @DisplayName("updateMainImageUrl: 단일 variant의 main_image_url을 갱신한다")
    void updateMainImageUrl_updatesSingleVariant() {
        String newUrl = "https://example.com/variant-new.jpg";

        int updated = productVariantJpaRepository.updateMainImageUrl(graph.variantId(), newUrl);
        testEntityManager.flush();
        testEntityManager.clear();

        ProductVariantEntity reloaded = testEntityManager.find(ProductVariantEntity.class, graph.variantId());
        assertThat(updated).isEqualTo(1);
        assertThat(reloaded.getMainImageUrl()).isEqualTo(newUrl);
    }

    @Test
    @DisplayName("updateMainImageUrlForIds: 여러 variant의 main_image_url을 일괄 갱신한다")
    void updateMainImageUrlForIds_updatesMultipleVariants() {
        String bulkUrl = "https://example.com/bulk.jpg";

        int updated = productVariantJpaRepository.updateMainImageUrlForIds(
                List.of(graph.variantId()),
                bulkUrl
        );
        testEntityManager.flush();
        testEntityManager.clear();

        ProductVariantEntity reloaded = testEntityManager.find(ProductVariantEntity.class, graph.variantId());
        assertThat(updated).isEqualTo(1);
        assertThat(reloaded.getMainImageUrl()).isEqualTo(bulkUrl);
    }

    @Test
    @DisplayName("findAllByIdInWithProductOrdered: variant를 id 순으로 product와 함께 조회한다")
    void findAllByIdInWithProductOrdered_ordersAndFetchesProduct() {
        List<ProductVariantEntity> variants = productVariantJpaRepository.findAllByIdInWithProductOrdered(
                List.of(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), graph.variantId())
        );

        assertThat(variants).hasSize(1);
        assertThat(variants.getFirst().getId()).isEqualTo(graph.variantId());
        assertThat(variants.getFirst().getProduct().getId()).isEqualTo(graph.productId());
    }

    @Test
    @DisplayName("@Version: variant 재고 변경 시 version이 증가한다")
    void versionIncrementsWhenStockChanges() {
        ProductVariantEntity variant = productVariantJpaRepository.findById(graph.variantId()).orElseThrow();
        assertThat(variant.getVersion()).isZero();

        variant.setStockQuantity(variant.getStockQuantity() - 1);
        productVariantJpaRepository.flush();

        assertThat(variant.getVersion()).isEqualTo(1);
    }

    @Configuration
    @Import(ProductDataAccessConfig.class)
    static class Config {
    }
}
