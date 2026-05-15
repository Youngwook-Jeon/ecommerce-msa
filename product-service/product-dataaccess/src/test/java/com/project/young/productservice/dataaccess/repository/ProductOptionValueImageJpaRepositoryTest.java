package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueImageEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = ProductOptionValueImageJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings("resource")
class ProductOptionValueImageJpaRepositoryTest {

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
    private ProductOptionValueImageJpaRepository productOptionValueImageJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private JpaRepositoryTestFixtures.CompositionGraph graph;

    @BeforeEach
    void setUp() {
        JpaRepositoryTestFixtures.truncateCompositionTables(testEntityManager);
        graph = JpaRepositoryTestFixtures.persistColorSizeProduct(testEntityManager);
    }

    @Test
    @DisplayName("findByStorageKeyAndProductOptionValue_Id: 저장된 이미지를 조회한다")
    void findByStorageKeyAndProductOptionValueId_findsSavedEntity() {
        ProductOptionValueEntity pov = testEntityManager.find(ProductOptionValueEntity.class, graph.redPovId());
        String storageKey = "products/" + graph.productId() + "/option-values/" + graph.redPovId() + "/a.jpg";
        ProductOptionValueImageEntity image = testEntityManager.persistAndFlush(
                JpaRepositoryTestFixtures.createPovImage(
                        pov, storageKey, ProductImageRoleEntity.GALLERY, 2, OptionStatusEntity.ACTIVE
                )
        );
        testEntityManager.clear();

        Optional<ProductOptionValueImageEntity> found = productOptionValueImageJpaRepository
                .findByStorageKeyAndProductOptionValue_Id(storageKey, graph.redPovId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(image.getId());
    }

    @Test
    @DisplayName("findByProductOptionValue_IdAndStatusOrderBySortOrderAsc: ACTIVE 이미지만 sort_order 순으로 조회한다")
    void findByProductOptionValueIdAndStatus_returnsActiveSorted() {
        ProductOptionValueEntity pov = testEntityManager.find(ProductOptionValueEntity.class, graph.redPovId());
        testEntityManager.persistAndFlush(JpaRepositoryTestFixtures.createPovImage(
                pov,
                "products/pov/gallery.jpg",
                ProductImageRoleEntity.GALLERY,
                0,
                OptionStatusEntity.ACTIVE
        ));
        testEntityManager.persistAndFlush(JpaRepositoryTestFixtures.createPovImage(
                pov,
                "products/pov/main.jpg",
                ProductImageRoleEntity.MAIN,
                5,
                OptionStatusEntity.ACTIVE
        ));
        testEntityManager.persistAndFlush(JpaRepositoryTestFixtures.createPovImage(
                pov,
                "products/pov/deleted.jpg",
                ProductImageRoleEntity.GALLERY,
                1,
                OptionStatusEntity.DELETED
        ));
        testEntityManager.clear();

        List<ProductOptionValueImageEntity> activeImages =
                productOptionValueImageJpaRepository.findByProductOptionValue_IdAndStatusOrderBySortOrderAsc(
                        graph.redPovId(),
                        OptionStatusEntity.ACTIVE
                );

        assertThat(activeImages).hasSize(2);
        assertThat(activeImages.getFirst().getSortOrder()).isEqualTo(0);
        assertThat(activeImages.get(1).getSortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("findByProductOptionValue_IdInAndStatusOrderBySortOrderAsc: 여러 POV의 ACTIVE 이미지를 조회한다")
    void findByProductOptionValueIdsAndStatus_returnsImagesForAllPovs() {
        ProductOptionValueEntity redPov = testEntityManager.find(ProductOptionValueEntity.class, graph.redPovId());
        ProductOptionValueEntity bluePov = testEntityManager.find(ProductOptionValueEntity.class, graph.bluePovId());
        testEntityManager.persistAndFlush(JpaRepositoryTestFixtures.createPovImage(
                redPov, "products/pov/red.jpg", ProductImageRoleEntity.MAIN, 0, OptionStatusEntity.ACTIVE
        ));
        testEntityManager.persistAndFlush(JpaRepositoryTestFixtures.createPovImage(
                bluePov, "products/pov/blue.jpg", ProductImageRoleEntity.MAIN, 0, OptionStatusEntity.ACTIVE
        ));
        testEntityManager.clear();

        List<ProductOptionValueImageEntity> images =
                productOptionValueImageJpaRepository.findByProductOptionValue_IdInAndStatusOrderBySortOrderAsc(
                        List.of(graph.redPovId(), graph.bluePovId()),
                        OptionStatusEntity.ACTIVE
                );

        assertThat(images).hasSize(2);
    }

    @Test
    @DisplayName("demoteActiveMainsToGallery: ACTIVE MAIN 이미지만 GALLERY로 변경한다")
    void demoteActiveMainsToGallery_updatesOnlyActiveMain() {
        ProductOptionValueEntity pov = testEntityManager.find(ProductOptionValueEntity.class, graph.redPovId());
        ProductOptionValueImageEntity activeMain = testEntityManager.persistAndFlush(
                JpaRepositoryTestFixtures.createPovImage(
                        pov, "products/pov/active-main.jpg", ProductImageRoleEntity.MAIN, 0, OptionStatusEntity.ACTIVE
                )
        );
        ProductOptionValueImageEntity deletedMain = testEntityManager.persistAndFlush(
                JpaRepositoryTestFixtures.createPovImage(
                        pov, "products/pov/deleted-main.jpg", ProductImageRoleEntity.MAIN, 1, OptionStatusEntity.DELETED
                )
        );

        int updated = productOptionValueImageJpaRepository.demoteActiveMainsToGallery(
                graph.redPovId(),
                ProductImageRoleEntity.GALLERY,
                ProductImageRoleEntity.MAIN,
                OptionStatusEntity.ACTIVE
        );
        testEntityManager.flush();
        testEntityManager.clear();

        ProductOptionValueImageEntity activeMainReloaded =
                testEntityManager.find(ProductOptionValueImageEntity.class, activeMain.getId());
        ProductOptionValueImageEntity deletedMainReloaded =
                testEntityManager.find(ProductOptionValueImageEntity.class, deletedMain.getId());

        assertThat(updated).isEqualTo(1);
        assertThat(activeMainReloaded.getRole()).isEqualTo(ProductImageRoleEntity.GALLERY);
        assertThat(deletedMainReloaded.getRole()).isEqualTo(ProductImageRoleEntity.MAIN);
    }

    @Test
    @DisplayName("softDelete/updateRole/updateSortOrder: 상태·role·sort_order 갱신 쿼리가 동작한다")
    void softDeleteAndUpdateRoleAndSortOrder_work() {
        ProductOptionValueEntity pov = testEntityManager.find(ProductOptionValueEntity.class, graph.redPovId());
        ProductOptionValueImageEntity image = testEntityManager.persistAndFlush(
                JpaRepositoryTestFixtures.createPovImage(
                        pov, "products/pov/x.jpg", ProductImageRoleEntity.GALLERY, 3, OptionStatusEntity.ACTIVE
                )
        );

        int roleUpdated = productOptionValueImageJpaRepository.updateRole(
                image.getId(),
                graph.redPovId(),
                ProductImageRoleEntity.MAIN,
                OptionStatusEntity.ACTIVE
        );
        int sortUpdated = productOptionValueImageJpaRepository.updateSortOrder(
                image.getId(),
                graph.redPovId(),
                1,
                OptionStatusEntity.ACTIVE
        );
        int deleted = productOptionValueImageJpaRepository.softDelete(
                image.getId(),
                graph.redPovId(),
                OptionStatusEntity.ACTIVE,
                OptionStatusEntity.DELETED
        );
        testEntityManager.flush();
        testEntityManager.clear();

        ProductOptionValueImageEntity reloaded =
                testEntityManager.find(ProductOptionValueImageEntity.class, image.getId());
        assertThat(roleUpdated).isEqualTo(1);
        assertThat(sortUpdated).isEqualTo(1);
        assertThat(deleted).isEqualTo(1);
        assertThat(reloaded.getRole()).isEqualTo(ProductImageRoleEntity.MAIN);
        assertThat(reloaded.getSortOrder()).isEqualTo(1);
        assertThat(reloaded.getStatus()).isEqualTo(OptionStatusEntity.DELETED);
    }

    @Configuration
    @Import(ProductDataAccessConfig.class)
    static class Config {
    }
}
