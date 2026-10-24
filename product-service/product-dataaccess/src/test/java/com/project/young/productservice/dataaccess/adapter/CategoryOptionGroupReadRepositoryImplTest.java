package com.project.young.productservice.dataaccess.adapter;

import com.fasterxml.uuid.Generators;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.service.CategoryOptionGroupQueryService;
import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.AttributeDefinitionEntity;
import com.project.young.productservice.dataaccess.entity.AttributeValueEntity;
import com.project.young.productservice.dataaccess.entity.CategoryAttributeEntity;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.mapper.AttributeCatalogDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.AttributeValueQueryRepository;
import com.project.young.productservice.domain.exception.OptionGroupNotFoundException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.AttributeStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired
    CategoryOptionGroupQueryService service;

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
        assertThat(repository.findAvailableByCategoryIdAndIds(category.getId(), Set.of(fixture.group().getId()))).isEmpty();
        assertThatThrownBy(() -> service.getAvailableOptionValues(category.getId(), List.of(fixture.group().getId())))
                .isInstanceOf(OptionGroupNotFoundException.class);
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

    @Test
    void returnsSelectedGroupsAndCanonicalValuesInThreeQueriesIncludingCategoryValidation() {
        var category = category("Bulk values category", null);
        var first = candidate(category, "test_bulk_ram", 0);
        var second = candidate(category, "test_bulk_storage", 1);
        var empty = candidate(category, "test_bulk_empty", 2);
        var unrelated = candidate(category, "test_bulk_unrequested", 3);
        var firstValue = optionValue(first, "RAM16", new BigDecimal("16"), 1, OptionStatusEntity.ACTIVE);
        var earlier = optionValue(first, "RAM32", new BigDecimal("32"), 0, OptionStatusEntity.ACTIVE);
        optionValue(first, "RAM64", new BigDecimal("64"), 2, OptionStatusEntity.INACTIVE);
        optionValue(first, "RAM128", new BigDecimal("128"), 3, OptionStatusEntity.DELETED);
        optionValue(second, "SSD512", new BigDecimal("512"), 0, OptionStatusEntity.ACTIVE);
        optionValue(unrelated, "OTHER", new BigDecimal("1"), 0, OptionStatusEntity.ACTIVE);
        // Legacy unlinked values must not be offered even when the containing group is mapped.
        entityManager.persist(OptionValueEntity.builder().id(UUID.randomUUID()).optionGroup(first.group())
                .value("LEGACY").displayName("Legacy unlinked value").status(OptionStatusEntity.ACTIVE).sortOrder(0).build());
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var results = service.getAvailableOptionValues(category.getId(), List.of(first.group().getId(),
                second.group().getId(), empty.group().getId(), first.group().getId()));

        assertThat(results).extracting(result -> result.group().id())
                .containsExactly(first.group().getId(), second.group().getId(), empty.group().getId());
        assertThat(results.getFirst().optionValues()).extracting(value -> value.id())
                .containsExactly(earlier.getId(), firstValue.getId());
        assertThat(results.getFirst().optionValues().get(1)).satisfies(value -> {
            assertThat(value.attributeValueId()).isEqualTo(firstValue.getAttributeValueId());
            assertThat(value.attributeId()).isEqualTo(first.definition().getId());
            assertThat(value.attributeDataType()).isEqualTo(AttributeDataType.NUMBER);
            assertThat(value.attributeValueDisplayName()).isEqualTo("Canonical RAM16");
            assertThat(value.numericValue()).isEqualByComparingTo("16");
            assertThat(value.textValue()).isNull();
            assertThat(value.booleanValue()).isNull();
            assertThat(value.dateValue()).isNull();
        });
        assertThat(results.get(1).optionValues()).singleElement()
                .satisfies(value -> assertThat(value.numericValue()).isEqualByComparingTo("512"));
        assertThat(results.get(2).optionValues()).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(statistics.getEntityLoadCount()).isEqualTo(1);
        assertThat(statistics.getCollectionFetchCount()).isZero();
    }

    @Test
    void rejectsGroupsFromAnotherCategoryWithoutLoadingValues() {
        var category = category("Requested category", null);
        var other = category("Other category", null);
        var allowed = candidate(category, "test_bulk_allowed", 0);
        var forbidden = candidate(other, "test_bulk_other", 0);
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        assertThatThrownBy(() -> service.getAvailableOptionValues(category.getId(),
                List.of(allowed.group().getId(), forbidden.group().getId())))
                .isInstanceOf(OptionGroupNotFoundException.class);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(AttributeDataType.class)
    void projectsCanonicalValuesForEverySupportedType(AttributeDataType type) {
        var category = category("Typed values category", null);
        var fixture = candidate(category, "test_bulk_type", 0, type);
        Object canonicalValue = switch (type) {
            case NUMBER -> new BigDecimal("16");
            case BOOLEAN -> false;
            case DATE -> LocalDate.of(2026, 3, 1);
            case TEXT, ENUM -> "Silver";
        };
        var option = optionValue(fixture, "CHOICE", canonicalValue, 0, OptionStatusEntity.ACTIVE);
        entityManager.flush();
        entityManager.clear();

        var values = repository.findActiveValuesByGroupIds(Set.of(fixture.group().getId()));

        assertThat(values).singleElement().satisfies(value -> {
            assertThat(value.attributeValueId()).isEqualTo(option.getAttributeValueId());
            assertThat(value.attributeDataType()).isEqualTo(type);
            switch (type) {
                case NUMBER -> assertThat(value.numericValue()).isEqualByComparingTo((BigDecimal) canonicalValue);
                case BOOLEAN -> assertThat(value.booleanValue()).isFalse();
                case DATE -> assertThat(value.dateValue()).isEqualTo(canonicalValue);
                case TEXT, ENUM -> assertThat(value.textValue()).isEqualTo(canonicalValue);
            }
        });
    }

    @Test
    void usesValueIdAsTieBreakerForEqualSortOrdersAndSkipsEmptyBatches() {
        var category = category("Value order category", null);
        var fixture = candidate(category, "test_bulk_order", 0);
        var first = optionValue(fixture, "RAM16", new BigDecimal("16"), 0, OptionStatusEntity.ACTIVE);
        var second = optionValue(fixture, "RAM32", new BigDecimal("32"), 0, OptionStatusEntity.ACTIVE);
        entityManager.flush();
        entityManager.clear();

        var values = repository.findActiveValuesByGroupIds(Set.of(fixture.group().getId()));

        assertThat(values).extracting(value -> value.id().toString())
                .containsExactlyElementsOf(List.of(first.getId().toString(), second.getId().toString()).stream().sorted().toList());
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        assertThat(repository.findActiveValuesByGroupIds(Set.of())).isEmpty();
        assertThat(repository.findAvailableByCategoryIdAndIds(category.getId(), Set.of())).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    private OptionValueEntity optionValue(Candidate fixture, String code, Object rawValue, int order, OptionStatusEntity status) {
        var canonical = new AttributeValueEntity();
        canonical.setId(UUID.randomUUID());
        canonical.setAttributeId(fixture.definition().getId());
        canonical.setDataType(fixture.definition().getDataType());
        canonical.setDisplayName("Canonical " + code);
        switch (fixture.definition().getDataType()) {
            case NUMBER -> canonical.setNumericValue((BigDecimal) rawValue);
            case BOOLEAN -> canonical.setBooleanValue((Boolean) rawValue);
            case DATE -> canonical.setDateValue((LocalDate) rawValue);
            case TEXT, ENUM -> canonical.setTextValue((String) rawValue);
        }
        entityManager.persist(canonical);
        var option = OptionValueEntity.builder().id(UUID.randomUUID()).optionGroup(fixture.group())
                .attributeId(fixture.definition().getId()).attributeValueId(canonical.getId())
                .value(code).displayName("Option " + code).sortOrder(order).status(status).build();
        entityManager.persist(option);
        return option;
    }

    private CategoryEntity category(String name, CategoryEntity parent) {
        var category = CategoryEntity.builder().name(name).status(CategoryStatusEntity.ACTIVE).parent(parent).build();
        entityManager.persist(category);
        return category;
    }

    private AttributeDefinitionEntity definition(String code) {
        return definition(code, AttributeDataType.NUMBER);
    }

    private AttributeDefinitionEntity definition(String code, AttributeDataType type) {
        var definition = new AttributeDefinitionEntity();
        definition.setId(UUID.randomUUID());
        definition.setCode(code);
        definition.setDisplayName("Memory capacity");
        definition.setDataType(type);
        definition.setCanonicalUnit(type == AttributeDataType.NUMBER ? "GB" : null);
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
        return candidate(category, code, order, AttributeDataType.NUMBER);
    }

    private Candidate candidate(CategoryEntity category, String code, int order, AttributeDataType type) {
        var definition = definition(code, type);
        var group = OptionGroupEntity.builder().id(UUID.randomUUID()).attributeId(definition.getId())
                .name("CHOICES_" + code).displayName("Memory choices").status(OptionStatusEntity.ACTIVE).build();
        entityManager.persist(group);
        return new Candidate(definition, group, policy(category, definition, order));
    }

    private record Candidate(AttributeDefinitionEntity definition, OptionGroupEntity group, CategoryAttributeEntity policy) {
    }

    @Configuration
    @Import({ProductDataAccessConfig.class, CategoryOptionGroupReadRepositoryImpl.class,
            CategoryOptionGroupQueryService.class, AttributeCatalogAdapter.class,
            AttributeCatalogDataAccessMapper.class, AttributeValueQueryRepository.class})
    static class Config {
        @Bean
        IdGenerator idGenerator() {
            return Generators.timeBasedEpochGenerator()::generate;
        }
    }
}
