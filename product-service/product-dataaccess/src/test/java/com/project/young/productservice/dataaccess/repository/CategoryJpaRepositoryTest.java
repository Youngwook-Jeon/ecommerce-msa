
package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.domain.entity.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = CategoryJpaRepositoryTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryJpaRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "true");
        registry.add("spring.jpa.show-sql", () -> "true");
    }

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @BeforeEach
    void setUp() {
        // Clean up data before each test
        categoryJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Should find active categories with their parent categories fetched")
    void shouldFindActiveCategories_WithParentCategories() {
        // Given
        CategoryEntity electronics = createCategory("Electronics", Category.STATUS_ACTIVE, null);
        CategoryEntity books = createCategory("Books", Category.STATUS_ACTIVE, null);
        CategoryEntity inactiveClothing = createCategory("Clothing", "INACTIVE", null);

        categoryJpaRepository.saveAll(List.of(electronics, books, inactiveClothing));

        CategoryEntity laptops = createCategory("Laptops", Category.STATUS_ACTIVE, electronics);
        categoryJpaRepository.save(laptops);
        categoryJpaRepository.flush();

        // When
        List<CategoryEntity> activeCategories = categoryJpaRepository.findAllWithParentByStatus(Category.STATUS_ACTIVE);

        // Then
        assertThat(activeCategories).hasSize(3);
        assertThat(activeCategories)
                .extracting(CategoryEntity::getName)
                .containsExactlyInAnyOrder("Electronics", "Books", "Laptops");

        // Verify N+1 problem prevention
        CategoryEntity foundLaptops = findCategoryByName(activeCategories, "Laptops");
        assertThat(foundLaptops.getParent()).isNotNull();
        assertThat(foundLaptops.getParent().getName()).isEqualTo("Electronics");
    }

    @Test
    @DisplayName("Should find all categories with their parent categories fetched")
    void shouldFindAllCategories_WithParentCategories() {
        // Given
        CategoryEntity electronics = createCategory("Electronics", Category.STATUS_ACTIVE, null);
        CategoryEntity books = createCategory("Books", Category.STATUS_ACTIVE, null);
        CategoryEntity inactiveClothing = createCategory("Clothing", "INACTIVE", null);

        categoryJpaRepository.saveAll(List.of(electronics, books, inactiveClothing));

        CategoryEntity laptops = createCategory("Laptops", Category.STATUS_ACTIVE, electronics);
        CategoryEntity fiction = createCategory("Fiction", "INACTIVE", books);
        categoryJpaRepository.saveAll(List.of(laptops, fiction));
        categoryJpaRepository.flush();

        // When
        List<CategoryEntity> allCategories = categoryJpaRepository.findAllWithParent();

        // Then
        assertThat(allCategories).hasSize(5);
        assertThat(allCategories)
                .extracting(CategoryEntity::getName)
                .containsExactlyInAnyOrder("Electronics", "Books", "Clothing", "Laptops", "Fiction");

        // Verify N+1 problem prevention - parent should be fetched
        CategoryEntity foundLaptops = findCategoryByName(allCategories, "Laptops");
        assertThat(foundLaptops.getParent()).isNotNull();
        assertThat(foundLaptops.getParent().getName()).isEqualTo("Electronics");

        CategoryEntity foundFiction = findCategoryByName(allCategories, "Fiction");
        assertThat(foundFiction.getParent()).isNotNull();
        assertThat(foundFiction.getParent().getName()).isEqualTo("Books");
    }

    @Test
    @DisplayName("Should check if category name exists")
    void shouldCheckIfCategoryNameExists() {
        // Given
        CategoryEntity electronics = createCategory("Electronics", Category.STATUS_ACTIVE, null);
        categoryJpaRepository.save(electronics);

        // When & Then
        assertThat(categoryJpaRepository.existsByName("Electronics")).isTrue();
        assertThat(categoryJpaRepository.existsByName("NonExistent")).isFalse();
    }

    @Test
    @DisplayName("Should check if category name exists excluding a specific ID")
    void shouldCheckIfCategoryNameExists_ExcludingSpecificId() {
        // Given
        CategoryEntity electronics = createCategory("Electronics", Category.STATUS_ACTIVE, null);
        CategoryEntity books = createCategory("Books", Category.STATUS_ACTIVE, null);
        categoryJpaRepository.saveAll(List.of(electronics, books));

        // When & Then
        assertThat(categoryJpaRepository.existsByNameAndIdNot("Electronics", books.getId())).isTrue();
        assertThat(categoryJpaRepository.existsByNameAndIdNot("Electronics", electronics.getId())).isFalse();
        assertThat(categoryJpaRepository.existsByNameAndIdNot("NonExistent", electronics.getId())).isFalse();
    }

    @Test
    @DisplayName("Should not find inactive categories when filtering by status")
    void shouldNotFindInactiveCategories() {
        // Given
        CategoryEntity activeCategory = createCategory("Active", Category.STATUS_ACTIVE, null);
        CategoryEntity inactiveCategory = createCategory("Inactive", "INACTIVE", null);
        categoryJpaRepository.saveAll(List.of(activeCategory, inactiveCategory));

        // When
        List<CategoryEntity> activeCategories = categoryJpaRepository.findAllWithParentByStatus(Category.STATUS_ACTIVE);

        // Then
        assertThat(activeCategories).hasSize(1);
        assertThat(activeCategories.getFirst().getName()).isEqualTo("Active");
    }

    @Test
    @DisplayName("Should find categories without a parent correctly")
    void shouldFindCategoriesWithoutParent() {
        // Given
        CategoryEntity rootCategory = createCategory("Root", Category.STATUS_ACTIVE, null);
        categoryJpaRepository.save(rootCategory);

        // When
        List<CategoryEntity> activeCategories = categoryJpaRepository.findAllWithParentByStatus(Category.STATUS_ACTIVE);

        // Then
        assertThat(activeCategories).hasSize(1);
        assertThat(activeCategories.getFirst().getName()).isEqualTo("Root");
        assertThat(activeCategories.getFirst().getParent()).isNull();
    }

    @Test
    @DisplayName("Should return empty list when no categories exist")
    void shouldReturnEmptyList_WhenNoCategoriesExist() {
        // When
        List<CategoryEntity> allCategories = categoryJpaRepository.findAllWithParent();
        List<CategoryEntity> activeCategories = categoryJpaRepository.findAllWithParentByStatus(Category.STATUS_ACTIVE);

        // Then
        assertThat(allCategories).isEmpty();
        assertThat(activeCategories).isEmpty();
    }

    @Test
    @DisplayName("Should handle mixed status categories in findAllWithParent")
    void shouldHandleMixedStatusCategories_InFindAllWithParent() {
        // Given
        CategoryEntity activeParent = createCategory("Active Parent", Category.STATUS_ACTIVE, null);
        CategoryEntity inactiveParent = createCategory("Inactive Parent", "INACTIVE", null);
        categoryJpaRepository.saveAll(List.of(activeParent, inactiveParent));

        CategoryEntity activeChild = createCategory("Active Child", Category.STATUS_ACTIVE, activeParent);
        CategoryEntity inactiveChild = createCategory("Inactive Child", "INACTIVE", inactiveParent);
        categoryJpaRepository.saveAll(List.of(activeChild, inactiveChild));

        // When
        List<CategoryEntity> allCategories = categoryJpaRepository.findAllWithParent();

        // Then
        assertThat(allCategories).hasSize(4);
        assertThat(allCategories)
                .extracting(CategoryEntity::getName)
                .containsExactlyInAnyOrder("Active Parent", "Inactive Parent", "Active Child", "Inactive Child");
    }

    private CategoryEntity createCategory(String name, String status, CategoryEntity parent) {
        return new CategoryEntity(null, name, status, parent, Collections.emptyList());
    }

    private CategoryEntity findCategoryByName(List<CategoryEntity> categories, String name) {
        return categories.stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Category not found: " + name));
    }

    @Configuration
    @Import(ProductDataAccessConfig.class)
    static class Config {
    }
}