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
    @DisplayName("Should not find inactive categories")
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