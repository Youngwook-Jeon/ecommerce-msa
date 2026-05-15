package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
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
@ContextConfiguration(classes = ProductOptionValueJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings("resource")
class ProductOptionValueJpaRepositoryTest {

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
    private ProductOptionValueJpaRepository productOptionValueJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private JpaRepositoryTestFixtures.CompositionGraph graph;

    @BeforeEach
    void setUp() {
        JpaRepositoryTestFixtures.truncateCompositionTables(testEntityManager);
        graph = JpaRepositoryTestFixtures.persistColorSizeProduct(testEntityManager);
    }

    @Test
    @DisplayName("existsByIdAndProductOptionGroup_Product_Id: 소속 POV면 true를 반환한다")
    void existsOwnedByProduct_returnsTrueForOwnedPov() {
        assertThat(productOptionValueJpaRepository.existsByIdAndProductOptionGroup_Product_Id(
                graph.redPovId(), graph.productId()
        )).isTrue();
    }

    @Test
    @DisplayName("existsByIdAndProductOptionGroup_Product_Id: 다른 상품이면 false를 반환한다")
    void existsOwnedByProduct_returnsFalseForOtherProduct() {
        UUID otherProductId = UUID.randomUUID();

        assertThat(productOptionValueJpaRepository.existsByIdAndProductOptionGroup_Product_Id(
                graph.redPovId(), otherProductId
        )).isFalse();
    }

    @Test
    @DisplayName("findProductIdByProductOptionValueId: POV의 상품 id를 조회한다")
    void findProductIdByProductOptionValueId_returnsProductId() {
        assertThat(productOptionValueJpaRepository.findProductIdByProductOptionValueId(graph.redPovId()))
                .contains(graph.productId());
    }

    @Test
    @DisplayName("findProductOptionGroupIdByProductOptionValueId: POV의 옵션 그룹 id를 조회한다")
    void findProductOptionGroupIdByProductOptionValueId_returnsGroupId() {
        assertThat(productOptionValueJpaRepository.findProductOptionGroupIdByProductOptionValueId(graph.redPovId()))
                .contains(graph.colorGroupId());
    }

    @Test
    @DisplayName("findPovIdAndGroupIdByProductId: 상품의 모든 POV-그룹 매핑을 조회한다")
    void findPovIdAndGroupIdByProductId_returnsAllMappings() {
        List<Object[]> rows = productOptionValueJpaRepository.findPovIdAndGroupIdByProductId(graph.productId());

        assertThat(rows).hasSize(3);
        assertThat(rows)
                .anyMatch(row -> graph.redPovId().equals(row[0]) && graph.colorGroupId().equals(row[1]));
        assertThat(rows)
                .anyMatch(row -> graph.bluePovId().equals(row[0]) && graph.colorGroupId().equals(row[1]));
        assertThat(rows)
                .anyMatch(row -> graph.largePovId().equals(row[0]) && graph.sizeGroupId().equals(row[1]));
    }

    @Test
    @DisplayName("findPovIdAndGroupIdByPovIds: 요청한 POV id만 매핑을 조회한다")
    void findPovIdAndGroupIdByPovIds_returnsRequestedMappings() {
        List<Object[]> rows = productOptionValueJpaRepository.findPovIdAndGroupIdByPovIds(
                List.of(graph.redPovId(), graph.largePovId())
        );

        assertThat(rows).hasSize(2);
        assertThat(rows)
                .anyMatch(row -> graph.redPovId().equals(row[0]) && graph.colorGroupId().equals(row[1]));
        assertThat(rows)
                .anyMatch(row -> graph.largePovId().equals(row[0]) && graph.sizeGroupId().equals(row[1]));
    }

    @Configuration
    @Import(ProductDataAccessConfig.class)
    static class Config {
    }
}
