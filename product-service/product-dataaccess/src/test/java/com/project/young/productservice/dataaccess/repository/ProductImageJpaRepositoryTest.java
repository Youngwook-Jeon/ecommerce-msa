package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductImageEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = ProductImageJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings("resource")
class ProductImageJpaRepositoryTest {

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
    private ProductImageJpaRepository productImageJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void setUp() {
        testEntityManager.getEntityManager()
                .createNativeQuery("TRUNCATE TABLE product_images, products RESTART IDENTITY CASCADE")
                .executeUpdate();
        testEntityManager.flush();
        testEntityManager.clear();
    }

    @Test
    @DisplayName("findByStorageKeyAndProduct_Id: 저장된 이미지를 조회한다")
    void findByStorageKeyAndProductId_FindsSavedEntity() {
        ProductEntity product = testEntityManager.persistAndFlush(createProduct("상품1"));
        ProductImageEntity image = testEntityManager.persistAndFlush(createImage(
                product,
                "products/" + product.getId() + "/a.jpg",
                ProductImageRoleEntity.GALLERY,
                2,
                OptionStatusEntity.ACTIVE
        ));
        testEntityManager.clear();

        Optional<ProductImageEntity> found = productImageJpaRepository
                .findByStorageKeyAndProduct_Id(image.getStorageKey(), product.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(image.getId());
    }

    @Test
    @DisplayName("demoteActiveMainsToGallery: ACTIVE MAIN 이미지만 GALLERY로 변경한다")
    void demoteActiveMainsToGallery_UpdatesOnlyActiveMain() {
        ProductEntity product = testEntityManager.persistAndFlush(createProduct("상품2"));
        ProductImageEntity activeMain = testEntityManager.persistAndFlush(createImage(
                product, "products/" + product.getId() + "/main.jpg", ProductImageRoleEntity.MAIN, 0, OptionStatusEntity.ACTIVE
        ));
        ProductImageEntity deletedMain = testEntityManager.persistAndFlush(createImage(
                product, "products/" + product.getId() + "/deleted-main.jpg", ProductImageRoleEntity.MAIN, 1, OptionStatusEntity.DELETED
        ));

        int updated = productImageJpaRepository.demoteActiveMainsToGallery(
                product.getId(),
                ProductImageRoleEntity.GALLERY,
                ProductImageRoleEntity.MAIN,
                OptionStatusEntity.ACTIVE
        );
        testEntityManager.flush();
        testEntityManager.clear();

        ProductImageEntity activeMainReloaded = testEntityManager.find(ProductImageEntity.class, activeMain.getId());
        ProductImageEntity deletedMainReloaded = testEntityManager.find(ProductImageEntity.class, deletedMain.getId());

        assertThat(updated).isEqualTo(1);
        assertThat(activeMainReloaded.getRole()).isEqualTo(ProductImageRoleEntity.GALLERY);
        assertThat(deletedMainReloaded.getRole()).isEqualTo(ProductImageRoleEntity.MAIN);
    }

    @Test
    @DisplayName("softDelete/updateRole: 상태 및 role 갱신 쿼리가 동작한다")
    void softDeleteAndUpdateRole_Works() {
        ProductEntity product = testEntityManager.persistAndFlush(createProduct("상품3"));
        ProductImageEntity image = testEntityManager.persistAndFlush(createImage(
                product, "products/" + product.getId() + "/x.jpg", ProductImageRoleEntity.GALLERY, 3, OptionStatusEntity.ACTIVE
        ));

        int roleUpdated = productImageJpaRepository.updateRole(
                image.getId(),
                product.getId(),
                ProductImageRoleEntity.MAIN,
                OptionStatusEntity.ACTIVE
        );
        int deleted = productImageJpaRepository.softDelete(
                image.getId(),
                product.getId(),
                OptionStatusEntity.ACTIVE,
                OptionStatusEntity.DELETED
        );
        testEntityManager.flush();
        testEntityManager.clear();

        ProductImageEntity reloaded = testEntityManager.find(ProductImageEntity.class, image.getId());
        assertThat(roleUpdated).isEqualTo(1);
        assertThat(deleted).isEqualTo(1);
        assertThat(reloaded.getRole()).isEqualTo(ProductImageRoleEntity.MAIN);
        assertThat(reloaded.getStatus()).isEqualTo(OptionStatusEntity.DELETED);
    }

    @Configuration
    @Import(ProductDataAccessConfig.class)
    static class Config {
    }

    private ProductEntity createProduct(String name) {
        return ProductEntity.builder()
                .name(name)
                .description("상품 설명은 20자 이상이어야 합니다.")
                .basePrice(new BigDecimal("10000"))
                .status(ProductStatusEntity.ACTIVE)
                .conditionType(ConditionTypeEntity.NEW)
                .brand("브랜드A")
                .mainImageUrl("https://example.com/default.jpg")
                .build();
    }

    private ProductImageEntity createImage(
            ProductEntity product,
            String storageKey,
            ProductImageRoleEntity role,
            int sortOrder,
            OptionStatusEntity status
    ) {
        return ProductImageEntity.builder()
                .product(product)
                .storageKey(storageKey)
                .publicUrl("https://pub.example/" + storageKey)
                .role(role)
                .sortOrder(sortOrder)
                .contentType("image/jpeg")
                .fileSize(1024L)
                .status(status)
                .build();
    }
}
