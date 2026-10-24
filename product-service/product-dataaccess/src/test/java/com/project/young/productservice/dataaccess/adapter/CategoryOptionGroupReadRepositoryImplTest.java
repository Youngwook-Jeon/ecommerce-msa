package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.AttributeDefinitionEntity;
import com.project.young.productservice.dataaccess.entity.CategoryAttributeEntity;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.AttributeStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = CategoryOptionGroupReadRepositoryImplTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryOptionGroupReadRepositoryImplTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("category_options").withUsername("testuser").withPassword("testpass");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    EntityManager entityManager;
    @Autowired
    CategoryOptionGroupReadRepositoryImpl repository;

    @Test
    void projectsOrderedGroupsInOneQueryWithoutLoadingEntitiesOrValues() {
        var category = category("Query laptop category", null);
        var last = candidate(category, "test_query_last", 20);
        var second = candidate(category, "test_query_b", 10);
        var first = candidate(category, "test_query_a", 10);
        first.policy().setRequired(true);
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var groups = repository.findAvailableByCategoryId(category.getId());

        assertThat(groups).extracting(group -> group.id())
                .containsExactly(first.group().getId(), second.group().getId(), last.group().getId());
        assertThat(groups.getFirst()).satisfies(group -> {
            assertThat(group.name()).isEqualTo(first.group().getName());
            assertThat(group.displayName()).isEqualTo("Memory choices");
            assertThat(group.attributeId()).isEqualTo(first.definition().getId());
            assertThat(group.attributeCode()).isEqualTo("test_query_a");
            assertThat(group.attributeDisplayName()).isEqualTo("Memory capacity");
            assertThat(group.attributeDataType()).isEqualTo(AttributeDataType.NUMBER);
            assertThat(group.canonicalUnit()).isEqualTo("GB");
            assertThat(group.attributeRequired()).isTrue();
            assertThat(group.displayOrder()).isEqualTo(10);
        });
        assertThat(groups.get(1).attributeRequired()).isFalse();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(statistics.getCollectionFetchCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "options_not_allowed", "inactive_definition", "inactive_group", "deleted_group"})
    void excludesIneligibleAssignments(String reason) {
        var category = category("Filtered category", null);
        var fixture = candidate(category, "test_query_filter", 0);
        switch (reason) {
            case "disabled" -> fixture.policy().setEnabled(false);
            case "options_not_allowed" -> fixture.policy().setOptionAllowed(false);
            case "inactive_definition" -> fixture.definition().setStatus(AttributeStatus.INACTIVE);
            case "inactive_group" -> fixture.group().setStatus(OptionStatusEntity.INACTIVE);
            case "deleted_group" -> fixture.group().setStatus(OptionStatusEntity.DELETED);
            default -> throw new IllegalArgumentException("Unknown test case");
        }
        if (!fixture.policy().isEnabled()) {
            fixture.policy().setOptionAllowed(false);
        }
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAvailableByCategoryId(category.getId())).isEmpty();
    }

    @Test
    void doesNotInheritParentPoliciesOrDependOnParentStatus() {
        var parent = category("Parent category", null);
        var child = category("Child category", parent);
        var grandchild = category("Grandchild category", child);
        candidate(parent, "test_query_parent", 0);
        var own = candidate(child, "test_query_child", 0);
        parent.setStatus(CategoryStatusEntity.DELETED);
        child.setStatus(CategoryStatusEntity.INACTIVE);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAvailableByCategoryId(child.getId()))
                .extracting(group -> group.id()).containsExactly(own.group().getId());
        assertThat(repository.findAvailableByCategoryId(grandchild.getId())).isEmpty();
    }

    @Test
    void excludesUnlinkedGroupsAndPoliciesWithoutDictionaryGroups() {
        var category = category("Unlinked category", null);
        var definition = definition("test_query_no_dictionary");
        policy(category, definition, 0);
        var legacyGroup = OptionGroupEntity.builder().id(UUID.randomUUID()).name("TEST_QUERY_LEGACY")
                .displayName("Legacy choices").status(OptionStatusEntity.ACTIVE).build();
        entityManager.persist(legacyGroup);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAvailableByCategoryId(category.getId())).isEmpty();
    }

    @Test
    void returnsEmptyListWhenCategoryHasNoAssignments() {
        var category = category("Empty category", null);
        entityManager.flush();
        assertThat(repository.findAvailableByCategoryId(category.getId())).isEmpty();
    }

    private CategoryEntity category(String name, CategoryEntity parent) {
        var category = CategoryEntity.builder().name(name).status(CategoryStatusEntity.ACTIVE).parent(parent).build();
        entityManager.persist(category);
        return category;
    }

    private AttributeDefinitionEntity definition(String code) {
        var definition = new AttributeDefinitionEntity();
        definition.setId(UUID.randomUUID());
        definition.setCode(code);
        definition.setDisplayName("Memory capacity");
        definition.setDataType(AttributeDataType.NUMBER);
        definition.setCanonicalUnit("GB");
        definition.setStatus(AttributeStatus.ACTIVE);
        entityManager.persist(definition);
        return definition;
    }

    private CategoryAttributeEntity policy(CategoryEntity category, AttributeDefinitionEntity definition, int order) {
        var policy = new CategoryAttributeEntity();
        policy.setId(UUID.randomUUID());
        policy.setCategoryId(category.getId());
        policy.setAttributeId(definition.getId());
        policy.setEnabled(true);
        policy.setOptionAllowed(true);
        policy.setDisplayOrder(order);
        entityManager.persist(policy);
        return policy;
    }

    private Candidate candidate(CategoryEntity category, String code, int order) {
        var definition = definition(code);
        var group = OptionGroupEntity.builder().id(UUID.randomUUID()).attributeId(definition.getId())
                .name("CHOICES_" + code).displayName("Memory choices").status(OptionStatusEntity.ACTIVE).build();
        entityManager.persist(group);
        return new Candidate(definition, group, policy(category, definition, order));
    }

    private record Candidate(AttributeDefinitionEntity definition, OptionGroupEntity group, CategoryAttributeEntity policy) {
    }

    @Configuration
    @Import({ProductDataAccessConfig.class, CategoryOptionGroupReadRepositoryImpl.class})
    static class Config {
    }
}
