package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.fasterxml.uuid.Generators;
import com.project.young.productservice.application.dto.command.CreateAttributeDefinitionCommand;
import com.project.young.productservice.application.dto.command.CreateAttributeValueCommand;
import com.project.young.productservice.application.dto.command.SetCategoryAttributeCommand;
import com.project.young.productservice.application.service.AttributeCatalogService;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.mapper.AttributeCatalogDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.dataaccess.repository.AttributeDefinitionJpaRepository;
import com.project.young.productservice.dataaccess.repository.AttributeValueJpaRepository;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.AttributeValue;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
import com.project.young.productservice.domain.exception.AttributeInactiveException;
import com.project.young.productservice.domain.exception.AttributeInUseException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = AttributeCatalogAdapterTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AttributeCatalogAdapterTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("attributes").withUsername("testuser").withPassword("testpass");

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
    AttributeCatalogService service;
    @Autowired
    CategoryJpaRepository categories;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager entityManager;
    @Autowired
    AttributeCatalogAdapter adapter;
    @Autowired
    AttributeDefinitionJpaRepository definitions;
    @Autowired
    AttributeValueJpaRepository values;

    @Test
    void categoryLookupDoesNotInitializeItsParent() {
        var parent = category("Lazy parent", null);
        var child = category("Lazy child", parent);
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var found = adapter.findCategory(child.getId()).orElseThrow();

        assertThat(found.id()).isEqualTo(child.getId());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        var parentProxy = entityManager.getReference(CategoryEntity.class, parent.getId());
        assertThat(Hibernate.isInitialized(parentProxy)).isFalse();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void categoryPolicyQueryDoesNotTraverseAncestors() {
        var root = category("Traversal root", null);
        var middle = category("Traversal middle", root);
        var leaf = category("Traversal leaf", middle);
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        assertThat(service.categoryPolicies(leaf.getId())).isEmpty();

        assertThat(statistics.getEntityLoadCount()).isEqualTo(1);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void loadsOnlyRequestedDefinitionsInOneQuery() {
        var first = definition("test_batch_first", AttributeDataType.TEXT);
        var second = definition("test_batch_second", AttributeDataType.NUMBER);
        definition("test_batch_unrelated", AttributeDataType.TEXT);
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var found = adapter.findDefinitionsByIds(Set.of(first.getId().getValue(), second.getId().getValue()));

        assertThat(found).extracting(item -> item.getId().getValue())
                .containsExactlyInAnyOrder(first.getId().getValue(), second.getId().getValue());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        statistics.clear();
        assertThat(adapter.findDefinitionsByIds(Set.of())).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void insertsAssignedIdsWithoutLookupSelects() {
        entityManager.flush();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var definition = new AttributeDefinition(new AttributeDefinitionId(UUID.randomUUID()), "test_no_select", "Memory", AttributeDataType.NUMBER, "GB");
        adapter.insertDefinition(definition);
        assertThat(statistics.getPrepareStatementCount()).isZero();
        entityManager.flush();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        statistics.clear();
        adapter.insertValue(new AttributeValue(new AttributeValueId(UUID.randomUUID()), new AttributeDefinitionId(definition.getId().getValue()), AttributeDataType.NUMBER,
                "16 GB", null, new BigDecimal("16"), null, null));
        assertThat(statistics.getPrepareStatementCount()).isZero();
        entityManager.flush();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void persistsRenamesThroughDirtyCheckingAfterReload() {
        var definition = definition("test_dirty_checking", AttributeDataType.NUMBER);
        var value = service.createValue(definition.getId().getValue(),
                new CreateAttributeValueCommand("16 GB", null, new BigDecimal("16"), null, null));
        entityManager.flush();
        entityManager.clear();

        service.renameDefinition(definition.getId().getValue(), "New memory label");
        service.renameValue(definition.getId().getValue(), value.getId().getValue(), "New value label");
        entityManager.flush();
        entityManager.clear();

        var storedDefinition = service.definition(definition.getId().getValue());
        assertThat(storedDefinition.getDisplayName()).isEqualTo("New memory label");
        assertThat(storedDefinition.getCode()).isEqualTo("test_dirty_checking");
        assertThat(storedDefinition.getDataType()).isEqualTo(AttributeDataType.NUMBER);
        assertThat(storedDefinition.getCanonicalUnit()).isEqualTo("GB");
        var storedValue = service.values(definition.getId().getValue()).getFirst();
        assertThat(storedValue.getDisplayName()).isEqualTo("New value label");
        assertThat(storedValue.getNumericValue()).isEqualByComparingTo("16");
    }

    @Test
    void updatingMissingRowsDoesNotInsertThem() {
        var definition = new AttributeDefinition(new AttributeDefinitionId(UUID.randomUUID()), "missing", "Missing", AttributeDataType.TEXT, null);
        assertThatThrownBy(() -> adapter.updateDefinition(definition))
                .isInstanceOf(AttributeNotFoundException.class);
        assertThatThrownBy(() -> adapter.updateValue(new AttributeValue(new AttributeValueId(UUID.randomUUID()), new AttributeDefinitionId(definition.getId().getValue()),
                AttributeDataType.TEXT, "Missing", "missing", null, null, null)))
                .isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void persistsTypedValuesAndOnlyRenamesTheirPresentation() {
        for (var type : AttributeDataType.values()) {
            var definition = definition("test_" + type.name().toLowerCase(), type);
            var command = switch (type) {
                case TEXT, ENUM -> new CreateAttributeValueCommand("Original", "BLACK", null, null, null);
                case NUMBER -> new CreateAttributeValueCommand("Original", null, new BigDecimal("16.00"), null, null);
                case BOOLEAN -> new CreateAttributeValueCommand("Original", null, null, false, null);
                case DATE -> new CreateAttributeValueCommand("Original", null, null, null, LocalDate.of(2026, 3, 1));
            };
            var value = service.createValue(definition.getId().getValue(), command);
            assertThat(service.values(definition.getId().getValue())).containsExactly(value);
            var renamed = service.renameValue(definition.getId().getValue(), value.getId().getValue(), "Renamed");
            assertThat(renamed.getId().getValue()).isEqualTo(value.getId().getValue());
            assertThat(renamed.getDataType()).isEqualTo(type);
            assertThat(renamed.getDisplayName()).isEqualTo("Renamed");
            assertThat(service.renameDefinition(definition.getId().getValue(), "New name").getCode()).isEqualTo(definition.getCode());
        }
    }

    @Test
    void categoryPoliciesNeverInheritFromParentsOrSiblings() {
        var root = category("Policy root", null);
        var child = category("Policy child", root);
        var sibling = category("Policy sibling", root);
        var definition = definition("test_ram", AttributeDataType.NUMBER);
        var siblingOnly = definition("test_sibling", AttributeDataType.TEXT);
        service.setPolicy(root.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(true, true, true, true, 10));
        service.setPolicy(sibling.getId(), siblingOnly.getId().getValue(), new SetCategoryAttributeCommand(true, false, false, true, 0));

        assertThat(service.categoryPolicies(child.getId())).isEmpty();
        service.setPolicy(child.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(false, false, false, false, 30));
        entityManager.flush();
        entityManager.clear();
        assertThat(service.categoryPolicies(child.getId()).getFirst().policy().enabled()).isFalse();

        service.setPolicy(child.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(true, false, false, true, 40));
        entityManager.flush();
        entityManager.clear();
        assertThat(service.categoryPolicies(child.getId()).getFirst().policy().required()).isFalse();
        assertThat(service.categoryPolicies(root.getId()).getFirst().policy().required()).isTrue();

        service.removePolicy(child.getId(), definition.getId().getValue());
        assertThat(service.categoryPolicies(child.getId())).isEmpty();
        assertThat(service.categoryPolicies(root.getId())).hasSize(1);
        assertThat(service.categoryPolicies(sibling.getId())).hasSize(1);
    }

    @Test
    void parentStatusAndReparentingDoNotChangeExplicitPolicies() {
        var root = category("Original parent", null);
        var child = category("Independent child", root);
        var otherParent = category("Other parent", null);
        var definition = definition("test_tree_independent", AttributeDataType.TEXT);
        service.setPolicy(child.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(true, true, false, true, 0));
        root.setStatus(CategoryStatusEntity.DELETED);
        otherParent.setStatus(CategoryStatusEntity.INACTIVE);
        categories.saveAndFlush(root);
        categories.saveAndFlush(otherParent);
        entityManager.clear();

        var before = service.categoryPolicies(child.getId()).getFirst();
        assertThat(before.usable()).isTrue();
        var storedChild = categories.findById(child.getId()).orElseThrow();
        storedChild.setParent(categories.getReferenceById(otherParent.getId()));
        categories.flush();
        entityManager.clear();

        var after = service.categoryPolicies(child.getId()).getFirst();
        assertThat(after.policy()).isEqualTo(before.policy());
        assertThat(after.usable()).isTrue();
    }

    @Test
    void rejectsDuplicateNormalizedNumbers() {
        var definition = definition("test_number", AttributeDataType.NUMBER);
        service.createValue(definition.getId().getValue(), new CreateAttributeValueCommand("16 GB", null, new BigDecimal("16"), null, null));
        assertThatThrownBy(() -> {
            service.createValue(definition.getId().getValue(),
                    new CreateAttributeValueCommand("16.0 GB", null, new BigDecimal("16.0"), null, null));
            values.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateCodes() {
        definition("test_duplicate", AttributeDataType.TEXT);
        assertThatThrownBy(() -> {
            definition("test_duplicate", AttributeDataType.TEXT);
            definitions.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsTypeSpoofingEvenWhenApplicationIsBypassed() {
        var definition = definition("test_numeric", AttributeDataType.NUMBER);
        entityManager.flush();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO attribute_values (id, attribute_id, data_type, display_name, text_value)
                VALUES (?, ?, 'TEXT', 'Wrong type', '16')
                """, UUID.randomUUID(), definition.getId().getValue()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preventsDeletingReferencedDefinitions() {
        var definition = definition("test_referenced", AttributeDataType.TEXT);
        var category = category("Referencing category", null);
        service.setPolicy(category.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(true, false, false, true, 0));
        assertThatThrownBy(() -> service.deleteDefinition(definition.getId().getValue()))
                .isInstanceOf(AttributeInUseException.class);
    }

    @Test
    void deactivationPreservesReferencesButRemovesEffectiveEligibility() {
        var root = category("Lifecycle root", null);
        var child = category("Lifecycle child", root);
        var definition = definition("test_lifecycle", AttributeDataType.NUMBER);
        var value = service.createValue(definition.getId().getValue(),
                new CreateAttributeValueCommand("16 GB", null, BigDecimal.valueOf(16), null, null));
        service.setPolicy(root.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(true, true, true, true, 10));
        service.setPolicy(child.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(true, true, true, true, 10));
        entityManager.flush();
        entityManager.clear();

        service.deactivateDefinition(definition.getId().getValue());
        service.deactivateDefinition(definition.getId().getValue());
        service.renameDefinition(definition.getId().getValue(), "Retired memory");
        entityManager.flush();
        entityManager.clear();

        assertThat(service.definition(definition.getId().getValue()).isActive()).isFalse();
        assertThat(service.definitions()).anySatisfy(item -> {
            assertThat(item.getId().getValue()).isEqualTo(definition.getId().getValue());
            assertThat(item.isActive()).isFalse();
        });
        assertThat(service.values(definition.getId().getValue())).containsExactly(value);
        var effective = service.categoryPolicies(child.getId()).getFirst();
        assertThat(effective.policy().enabled()).isTrue();
        assertThat(effective.policy().required()).isTrue();
        assertThat(effective.usable()).isFalse();
        assertThat(effective.optionAvailable()).isFalse();
        assertThat(effective.filterAvailable()).isFalse();
        assertThat(service.categoryPolicies(root.getId())).hasSize(1);
        assertThatThrownBy(() -> service.createValue(definition.getId().getValue(),
                new CreateAttributeValueCommand("32 GB", null, BigDecimal.valueOf(32), null, null)))
                .isInstanceOf(AttributeInactiveException.class);
        assertThatThrownBy(() -> service.setPolicy(child.getId(), definition.getId().getValue(),
                new SetCategoryAttributeCommand(true, false, false, true, 0)))
                .isInstanceOf(AttributeInactiveException.class);
        assertThat(service.setPolicy(child.getId(), definition.getId().getValue(),
                new SetCategoryAttributeCommand(false, false, false, false, 0)).enabled()).isFalse();
        assertThat(service.renameValue(definition.getId().getValue(), value.getId().getValue(), "Historical 16 GB").getNumericValue())
                .isEqualByComparingTo("16");
        assertThatThrownBy(() -> service.deleteDefinition(definition.getId().getValue())).isInstanceOf(AttributeInUseException.class);
    }

    @Test
    void valueReferencesPreventDeletionWithSpecificError() {
        var definition = definition("test_value_reference", AttributeDataType.TEXT);
        service.createValue(definition.getId().getValue(), new CreateAttributeValueCommand("Existing", "existing", null, null, null));
        assertThatThrownBy(() -> service.deleteDefinition(definition.getId().getValue())).isInstanceOf(AttributeInUseException.class);
    }

    @Test
    void restrictiveForeignKeyStillProtectsReferencesWhenServiceIsBypassed() {
        var definition = definition("test_fk_reference", AttributeDataType.TEXT);
        var category = category("FK reference", null);
        service.setPolicy(category.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(true, false, false, true, 0));
        entityManager.flush();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM attribute_definitions WHERE id = ?", definition.getId().getValue()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void scopesValuesToTheirDefinitionAndAllowsExplicitCleanup() {
        var first = definition("test_first", AttributeDataType.TEXT);
        var second = definition("test_second", AttributeDataType.TEXT);
        var value = service.createValue(first.getId().getValue(), new CreateAttributeValueCommand("Black", "BLACK", null, null, null));
        assertThatThrownBy(() -> service.renameValue(second.getId().getValue(), value.getId().getValue(), "Wrong"))
                .isInstanceOf(AttributeNotFoundException.class);
        service.deleteValue(first.getId().getValue(), value.getId().getValue());
        service.deleteDefinition(first.getId().getValue());
        assertThat(service.values(second.getId().getValue())).isEmpty();
    }

    @Test
    void seedsLaptopMetadataWithoutCreatingProductSpecifications() {
        var laptop = categories.findAll().stream().filter(category -> category.getName().equals("Laptops"))
                .findFirst().orElseThrow();
        assertThat(service.categoryPolicies(laptop.getId()))
                .extracting(result -> result.attribute().getCode())
                .containsExactly("ram_capacity", "storage_capacity", "color", "weight", "release_date");
        assertThat(service.definitions()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void preventsPhysicalDeletionOfCategoriesWithPolicies() {
        var category = category("Protected category", null);
        var definition = definition("test_protected_category", AttributeDataType.TEXT);
        service.setPolicy(category.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(true, false, false, true, 0));
        entityManager.flush();

        assertThatThrownBy(() -> jdbc.update("DELETE FROM categories WHERE id = ?", category.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void softDeletionPreservesPoliciesAndBlocksResolution() {
        var category = category("Soft-deleted category", null);
        var definition = definition("test_soft_deleted_category", AttributeDataType.TEXT);
        service.setPolicy(category.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(true, false, false, true, 0));

        category.setStatus(CategoryStatusEntity.DELETED);
        categories.saveAndFlush(category);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM category_attributes WHERE category_id = ?",
                Long.class, category.getId())).isEqualTo(1L);
        assertThatThrownBy(() -> service.categoryPolicies(category.getId()))
                .isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void allowsPhysicalDeletionAfterExplicitPolicyCleanup() {
        var category = category("Cleanup category", null);
        var definition = definition("test_cleanup_category", AttributeDataType.TEXT);
        service.setPolicy(category.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(true, false, false, true, 0));

        service.removePolicy(category.getId(), definition.getId().getValue());

        assertThat(jdbc.update("DELETE FROM categories WHERE id = ?", category.getId())).isEqualTo(1);
    }

    private AttributeDefinition definition(String code, AttributeDataType type) {
        return service.createDefinition(new CreateAttributeDefinitionCommand(code, code, type,
                type == AttributeDataType.NUMBER ? "GB" : null));
    }

    private CategoryEntity category(String name, CategoryEntity parent) {
        return categories.saveAndFlush(CategoryEntity.builder().name(name).status(CategoryStatusEntity.ACTIVE)
                .parent(parent).build());
    }

    @Configuration
    @Import({ProductDataAccessConfig.class, AttributeCatalogAdapter.class,
            AttributeCatalogDataAccessMapper.class, AttributeCatalogService.class})
    static class Config {
        @Bean
        IdGenerator idGenerator() {
            return Generators.timeBasedEpochGenerator()::generate;
        }
    }
}
