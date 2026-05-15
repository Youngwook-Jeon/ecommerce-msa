package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = ProductOptionGroupJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings("resource")
class ProductOptionGroupJpaRepositoryTest {

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
    private ProductOptionGroupJpaRepository productOptionGroupJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private JpaRepositoryTestFixtures.CompositionGraph graph;

    @BeforeEach
    void setUp() {
        JpaRepositoryTestFixtures.truncateCompositionTables(testEntityManager);
        graph = JpaRepositoryTestFixtures.persistColorSizeProduct(testEntityManager);
    }

    @Test
    @DisplayName("findActiveVisualGroupByProductId: drives_variant_images=true인 ACTIVE 그룹을 조회한다")
    void findActiveVisualGroupByProductId_returnsVisualGroup() {
        productOptionGroupJpaRepository.clearVisualFlagsForProduct(graph.productId(), OptionStatusEntity.ACTIVE);
        int updated = productOptionGroupJpaRepository.updateDrivesVariantImages(
                graph.productId(),
                graph.colorGroupId(),
                true,
                OptionStatusEntity.ACTIVE
        );
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(productOptionGroupJpaRepository.findActiveVisualGroupByProductId(
                graph.productId(), OptionStatusEntity.ACTIVE
        )).map(ProductOptionGroupEntity::getId).contains(graph.colorGroupId());
    }

    @Test
    @DisplayName("clearVisualFlagsForProduct: 상품의 visual 플래그를 모두 해제한다")
    void clearVisualFlagsForProduct_clearsAllVisualFlags() {
        productOptionGroupJpaRepository.updateDrivesVariantImages(
                graph.productId(), graph.colorGroupId(), true, OptionStatusEntity.ACTIVE
        );
        testEntityManager.flush();

        int cleared = productOptionGroupJpaRepository.clearVisualFlagsForProduct(
                graph.productId(), OptionStatusEntity.ACTIVE
        );
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(cleared).isEqualTo(1);
        assertThat(productOptionGroupJpaRepository.findActiveVisualGroupByProductId(
                graph.productId(), OptionStatusEntity.ACTIVE
        )).isEmpty();

        ProductOptionGroupEntity colorGroup = testEntityManager.find(ProductOptionGroupEntity.class, graph.colorGroupId());
        assertThat(colorGroup.isDrivesVariantImages()).isFalse();
    }

    @Test
    @DisplayName("updateDrivesVariantImages: visual 그룹을 다른 그룹으로 변경할 수 있다")
    void updateDrivesVariantImages_switchesVisualGroup() {
        productOptionGroupJpaRepository.clearVisualFlagsForProduct(graph.productId(), OptionStatusEntity.ACTIVE);
        productOptionGroupJpaRepository.updateDrivesVariantImages(
                graph.productId(), graph.colorGroupId(), true, OptionStatusEntity.ACTIVE
        );
        testEntityManager.flush();

        productOptionGroupJpaRepository.clearVisualFlagsForProduct(graph.productId(), OptionStatusEntity.ACTIVE);
        int updated = productOptionGroupJpaRepository.updateDrivesVariantImages(
                graph.productId(), graph.sizeGroupId(), true, OptionStatusEntity.ACTIVE
        );
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(productOptionGroupJpaRepository.findActiveVisualGroupByProductId(
                graph.productId(), OptionStatusEntity.ACTIVE
        )).map(ProductOptionGroupEntity::getId).contains(graph.sizeGroupId());

        ProductOptionGroupEntity colorGroup = testEntityManager.find(ProductOptionGroupEntity.class, graph.colorGroupId());
        ProductOptionGroupEntity sizeGroup = testEntityManager.find(ProductOptionGroupEntity.class, graph.sizeGroupId());
        assertThat(colorGroup.isDrivesVariantImages()).isFalse();
        assertThat(sizeGroup.isDrivesVariantImages()).isTrue();
    }

    @Test
    @DisplayName("updateDrivesVariantImages: 존재하지 않는 그룹이면 0을 반환한다")
    void updateDrivesVariantImages_returnsZeroWhenGroupNotFound() {
        int updated = productOptionGroupJpaRepository.updateDrivesVariantImages(
                graph.productId(),
                java.util.UUID.randomUUID(),
                true,
                OptionStatusEntity.ACTIVE
        );

        assertThat(updated).isZero();
    }

    @Configuration
    @Import(ProductDataAccessConfig.class)
    static class Config {
    }
}
