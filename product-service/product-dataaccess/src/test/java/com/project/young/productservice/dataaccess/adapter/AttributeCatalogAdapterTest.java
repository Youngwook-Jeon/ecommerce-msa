package com.project.young.productservice.dataaccess.adapter;

import com.fasterxml.uuid.Generators;
import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.command.AddOptionValueCommand;
import com.project.young.productservice.application.dto.command.AddOptionValuesCommand;
import com.project.young.productservice.application.dto.command.AddProductOptionValueCommand;
import com.project.young.productservice.application.dto.command.CreateAttributeDefinitionCommand;
import com.project.young.productservice.application.dto.command.CreateAttributeValueCommand;
import com.project.young.productservice.application.dto.command.CreateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.SetCategoryAttributeCommand;
import com.project.young.productservice.application.dto.command.UpdateOptionGroupCommand;
import com.project.young.productservice.application.exception.OptionGroupVersionConflictException;
import com.project.young.productservice.application.mapper.OptionGroupDataMapper;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.port.output.OptionGroupDetailsPort;
import com.project.young.productservice.application.service.AttributeCatalogService;
import com.project.young.productservice.application.service.OptionAttributeValidator;
import com.project.young.productservice.application.service.OptionGroupApplicationService;
import com.project.young.productservice.application.service.ProductOptionPolicyValidator;
import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.AttributeDefinitionEntity;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.mapper.AttributeCatalogDataAccessMapper;
import com.project.young.productservice.dataaccess.mapper.OptionGroupAggregateMapper;
import com.project.young.productservice.dataaccess.mapper.OptionGroupDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.AttributeDefinitionJpaRepository;
import com.project.young.productservice.dataaccess.repository.AttributeValueJpaRepository;
import com.project.young.productservice.dataaccess.repository.AttributeValueQueryRepository;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.AttributeValue;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.AttributeInUseException;
import com.project.young.productservice.domain.exception.AttributeInactiveException;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
import com.project.young.productservice.domain.exception.OptionDomainException;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.service.OptionGroupDomainService;
import com.project.young.productservice.domain.service.OptionGroupDomainServiceImpl;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.OptionGroupAttributeBinding;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
    OptionGroupApplicationService optionService;
    @Autowired
    OptionGroupAggregateMapper aggregateMapper;
    @Autowired
    OptionGroupRepository optionRepository;
    @Autowired
    ProductOptionPolicyValidator productOptionPolicyValidator;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    DataSource dataSource;
    @Autowired
    OptionGroupDetailsPort detailsPort;
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
    void loadsValueIdsInOneQueryScopedToTheAttribute() {
        var first = definition("test_batch_value_ram", AttributeDataType.NUMBER);
        var second = definition("test_batch_value_storage", AttributeDataType.NUMBER);
        var firstValue = service.createValue(first.getId().getValue(),
                new CreateAttributeValueCommand("16 GB", null, new BigDecimal("16"), null, null));
        var secondValue = service.createValue(first.getId().getValue(),
                new CreateAttributeValueCommand("32 GB", null, new BigDecimal("32"), null, null));
        var unrelated = service.createValue(second.getId().getValue(),
                new CreateAttributeValueCommand("512 GB", null, new BigDecimal("512"), null, null));
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var found = adapter.findValueIds(first.getId().getValue(), Set.of(firstValue.getId().getValue(),
                secondValue.getId().getValue(), unrelated.getId().getValue(), UUID.randomUUID()));

        assertThat(found).containsExactlyInAnyOrder(firstValue.getId().getValue(), secondValue.getId().getValue());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isZero();
        statistics.clear();
        assertThat(adapter.findValueIds(first.getId().getValue(), Set.of())).isEmpty();
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

    @Test
    void persistsDictionaryBindingsAndKeepsThemOnUpdates() {
        var definition = definition("test_option_ram", AttributeDataType.NUMBER);
        var value = service.createValue(definition.getId().getValue(),
                new CreateAttributeValueCommand("16 GB", null, new BigDecimal("16"), null, null));
        UUID groupId = optionGroup(definition, "TEST_RAM");
        var added = optionService.addOptionValues(groupId, new AddOptionValuesCommand(List.of(
                new AddOptionValueCommand(value.getId().getValue(), "RAM16", "16 GB", 0))));
        entityManager.flush();
        entityManager.clear();

        var group = optionRepository.findById(new OptionGroupId(groupId)).orElseThrow();
        assertThat(group.getAttributeId()).isEqualTo(definition.getId());
        assertThat(group.getOptionValues()).singleElement().satisfies(option ->
                assertThat(option.getAttributeValueId()).isEqualTo(value.getId()));
        group.changeDisplayName("Memory choices");
        optionRepository.update(group);
        entityManager.flush();
        entityManager.clear();
        var updated = optionRepository.findById(group.getId()).orElseThrow();
        assertThat(updated.getAttributeId()).isEqualTo(definition.getId());
        assertThat(updated.getOptionValues().getFirst().getId().getValue()).isEqualTo(added.getFirst().id());
        assertThat(updated.getOptionValues().getFirst().getAttributeValueId()).isEqualTo(value.getId());
    }

    @Test
    void rejectsAttributeDeletionReferencedOnlyByAnOptionGroup() {
        var definition = definition("test_option_group_reference", AttributeDataType.NUMBER);
        optionGroup(definition, "TEST_GROUP_REFERENCE");
        entityManager.flush();
        assertThatThrownBy(() -> service.deleteDefinition(definition.getId().getValue()))
                .isInstanceOf(AttributeInUseException.class);
    }

    @Test
    void rejectsDeletionOfReferencedCanonicalValueEvenAfterOptionSoftDeletion() {
        var definition = definition("test_option_value_reference", AttributeDataType.NUMBER);
        var value = service.createValue(definition.getId().getValue(),
                new CreateAttributeValueCommand("16 GB", null, new BigDecimal("16"), null, null));
        UUID groupId = optionGroup(definition, "TEST_VALUE_REFERENCE");
        optionService.addOptionValues(groupId, new AddOptionValuesCommand(List.of(
                new AddOptionValueCommand(value.getId().getValue(), "RAM16", "16 GB", 0))));
        optionService.deleteOptionGroup(groupId);
        entityManager.flush();

        assertThatThrownBy(() -> service.deleteValue(definition.getId().getValue(), value.getId().getValue()))
                .isInstanceOf(AttributeInUseException.class);
    }

    @Test
    void databaseRejectsMultipleGroupsForSameAttribute() {
        var definition = definition("test_duplicate_option_group", AttributeDataType.NUMBER);
        optionGroup(definition, "TEST_UNIQUE_ONE");
        entityManager.flush();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO option_groups (id, name, display_name, attribute_id) VALUES (?, 'TEST_UNIQUE_TWO', 'Second group', ?)",
                UUID.randomUUID(), definition.getId().getValue()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsCanonicalValueFromAnotherAttribute() {
        var first = definition("test_fk_ram", AttributeDataType.NUMBER);
        var second = definition("test_fk_storage", AttributeDataType.NUMBER);
        var value = service.createValue(second.getId().getValue(),
                new CreateAttributeValueCommand("512 GB", null, new BigDecimal("512"), null, null));
        UUID groupId = optionGroup(first, "TEST_COMPOSITE_FK");
        entityManager.flush();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO option_values (id, option_group_id, attribute_id, attribute_value_id, value, display_name, sort_order, status) "
                        + "VALUES (?, ?, ?, ?, 'WRONG', 'Wrong value', 0, 'ACTIVE')",
                UUID.randomUUID(), groupId, first.getId().getValue(), value.getId().getValue()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSameCanonicalValueUnderDifferentOptionCodes() {
        var definition = definition("test_duplicate_canonical", AttributeDataType.NUMBER);
        var value = service.createValue(definition.getId().getValue(),
                new CreateAttributeValueCommand("16 GB", null, new BigDecimal("16"), null, null));
        UUID groupId = optionGroup(definition, "TEST_DUPLICATE_CANONICAL");
        optionService.addOptionValues(groupId, new AddOptionValuesCommand(List.of(
                new AddOptionValueCommand(value.getId().getValue(), "RAM16", "16 GB", 0))));
        entityManager.flush();
        assertThatThrownBy(() -> optionService.addOptionValues(groupId, new AddOptionValuesCommand(List.of(
                new AddOptionValueCommand(value.getId().getValue(), "ALIAS16", "16 GB alias", 1)))))
                .isInstanceOf(OptionDomainException.class);
    }

    @Test
    void productOptionValidationLocksDictionaryAndDefinitionAndRejectsRemovedPolicy() {
        var category = category("Product option policy category", null);
        var definition = definition("test_product_option_policy", AttributeDataType.NUMBER);
        var value = service.createValue(definition.getId().getValue(),
                new CreateAttributeValueCommand("16 GB", null, new BigDecimal("16"), null, null));
        service.setPolicy(category.getId(), definition.getId().getValue(),
                new SetCategoryAttributeCommand(true, false, true, false, 0));
        UUID groupId = optionGroup(definition, "TEST_PRODUCT_POLICY");
        var added = optionService.addOptionValues(groupId, new AddOptionValuesCommand(List.of(
                new AddOptionValueCommand(value.getId().getValue(), "RAM16", "16 GB", 0))));
        entityManager.flush();
        entityManager.clear();
        var product = Product.builder().productId(new ProductId(UUID.randomUUID())).categoryId(new CategoryId(category.getId()))
                .name("Product").description("Product option policy validation test")
                .brand("Brand").basePrice(new Money(BigDecimal.ONE)).conditionType(ConditionType.NEW)
                .mainImageUrl("https://example.com/product.jpg").build();
        var commands = List.of(AddProductOptionValueCommand.builder().optionValueId(added.getFirst().id()).build());

        productOptionPolicyValidator.validate(product, new OptionGroupId(groupId), commands);

        assertThat(entityManager.getLockMode(entityManager.find(OptionGroupEntity.class, groupId)))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(entityManager.getLockMode(entityManager.find(AttributeDefinitionEntity.class, definition.getId().getValue())))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        service.removePolicy(category.getId(), definition.getId().getValue());
        assertThatThrownBy(() -> productOptionPolicyValidator.validate(product, new OptionGroupId(groupId), commands))
                .isInstanceOf(ProductDomainException.class);
    }

    @Test
    void updatesAlreadyLockedDictionaryWithoutAnotherLookupSelect() {
        var definition = definition("test_locked_dictionary_update", AttributeDataType.NUMBER);
        UUID groupId = optionGroup(definition, "TEST_LOCKED_DICTIONARY_UPDATE");
        entityManager.flush();
        entityManager.clear();
        var group = optionRepository.findByIdForUpdate(new OptionGroupId(groupId)).orElseThrow();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        group.changeDisplayName("Updated choices");
        optionRepository.update(group);

        assertThat(statistics.getPrepareStatementCount()).isZero();
        entityManager.flush();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20})
    void find_by_id_for_update_explicitly_fetches_values_before_mapping(int valueCount) {
        var definition = definition("test_locked_loading_" + valueCount, AttributeDataType.NUMBER);
        UUID groupId = optionGroup(definition, "TEST_LOCKED_LOADING_" + valueCount);
        List<AddOptionValueCommand> commands = new ArrayList<>();
        for (int index = 1; index <= valueCount; index++) {
            var value = service.createValue(definition.getId().getValue(),
                    new CreateAttributeValueCommand("Memory " + index, null, BigDecimal.valueOf(index), null, null));
            commands.add(new AddOptionValueCommand(value.getId().getValue(), "RAM" + index, "Memory " + index, index));
        }
        optionService.addOptionValues(groupId, new AddOptionValuesCommand(commands));
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var group = optionRepository.findByIdForUpdate(new OptionGroupId(groupId)).orElseThrow();

        assertThat(group.getOptionValues()).hasSize(valueCount);
        assertThat(group.getOptionValues()).allSatisfy(value -> assertThat(value.getAttributeValueId()).isNotNull());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(statistics.getCollectionFetchCount()).isZero();
        assertThat(statistics.getCollectionLoadCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isEqualTo(valueCount + 1);
        assertThat(Hibernate.isInitialized(entityManager.getReference(AttributeDefinitionEntity.class,
                definition.getId().getValue()))).isFalse();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 20})
    void group_information_updates_never_initialize_values(int valueCount) {
        var definition = definition("test_details_only_" + valueCount, AttributeDataType.NUMBER);
        UUID groupId = optionGroup(definition, "TEST_DETAILS_ONLY_" + valueCount);
        List<AddOptionValueCommand> commands = new ArrayList<>();
        for (int index = 1; index <= valueCount; index++) {
            var value = service.createValue(definition.getId().getValue(),
                    new CreateAttributeValueCommand("Memory " + index, null, BigDecimal.valueOf(index), null, null));
            commands.add(new AddOptionValueCommand(value.getId().getValue(), "RAM" + index, "Memory " + index, index));
        }
        if (!commands.isEmpty()) {
            optionService.addOptionValues(groupId, new AddOptionValuesCommand(commands));
        }
        entityManager.flush();
        long expectedVersion = entityManager.find(OptionGroupEntity.class, groupId).getVersion();
        entityManager.clear();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var updated = optionService.updateOptionGroup(groupId, UpdateOptionGroupCommand.builder()
                .displayName("Updated memory choices").status(OptionStatus.INACTIVE).build(), expectedVersion);

        assertThat(updated.displayName()).isEqualTo("Updated memory choices");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isEqualTo(1);
        assertThat(statistics.getCollectionLoadCount()).isZero();
        assertThat(statistics.getCollectionFetchCount()).isZero();
        var entity = entityManager.getReference(OptionGroupEntity.class, groupId);
        assertThat(Hibernate.isInitialized(entity.getOptionValues())).isFalse();
        assertThat(entityManager.getLockMode(entity)).isNotIn(LockModeType.PESSIMISTIC_READ,
                LockModeType.PESSIMISTIC_WRITE, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        assertThat(entity.getAttributeId()).isEqualTo(definition.getId().getValue());
        entityManager.flush();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        entityManager.clear();
        var statuses = entityManager.createQuery("select v.status from OptionValueEntity v where v.optionGroup.id = :id",
                OptionStatusEntity.class).setParameter("id", groupId).getResultList();
        assertThat(statuses).hasSize(valueCount).allMatch(status -> status == OptionStatusEntity.ACTIVE);
    }

    @Test
    void mapper_rejects_unloaded_values_without_issuing_sql() {
        var definition = definition("test_mapper_no_lazy", AttributeDataType.NUMBER);
        UUID groupId = optionGroup(definition, "TEST_MAPPER_NO_LAZY");
        entityManager.flush();
        entityManager.clear();
        var entity = entityManager.find(OptionGroupEntity.class, groupId);
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        assertThatThrownBy(() -> aggregateMapper.toOptionGroup(entity, entity.getOptionValues()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("explicitly loaded");

        assertThat(Hibernate.isInitialized(entity.getOptionValues())).isFalse();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void aggregate_deletion_explicitly_loads_and_soft_deletes_values() {
        var definition = definition("test_aggregate_delete_values", AttributeDataType.NUMBER);
        UUID groupId = optionGroup(definition, "TEST_AGGREGATE_DELETE_VALUES");
        var value = service.createValue(definition.getId().getValue(),
                new CreateAttributeValueCommand("16 GB", null, BigDecimal.valueOf(16), null, null));
        optionService.addOptionValues(groupId, new AddOptionValuesCommand(List.of(
                new AddOptionValueCommand(value.getId().getValue(), "RAM16", "16 GB", 0))));
        entityManager.flush();
        entityManager.clear();

        optionService.deleteOptionGroup(groupId);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(OptionGroupEntity.class, groupId).getStatus()).isEqualTo(OptionStatusEntity.DELETED);
        assertThat(entityManager.createQuery("select v.status from OptionValueEntity v where v.optionGroup.id = :id",
                OptionStatusEntity.class).setParameter("id", groupId).getResultList())
                .containsExactly(OptionStatusEntity.DELETED);
    }

    @Test
    void parent_binding_lookup_reads_only_scalar_ids_without_loading_entities() {
        var definition = definition("test_scalar_parent_binding", AttributeDataType.NUMBER);
        UUID groupId = optionGroup(definition, "TEST_SCALAR_PARENT_BINDING");
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var binding = optionRepository.findAttributeBinding(new OptionGroupId(groupId)).orElseThrow();

        assertThat(binding).isEqualTo(new OptionGroupAttributeBinding(new OptionGroupId(groupId), definition.getId()));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(Hibernate.isInitialized(entityManager.getReference(OptionGroupEntity.class, groupId))).isFalse();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void parent_binding_lookup_distinguishes_legacy_group_from_missing_group() {
        var legacy = OptionGroupEntity.builder().id(UUID.randomUUID()).name("TEST_LEGACY_BINDING")
                .displayName("Legacy").status(OptionStatusEntity.ACTIVE).build();
        entityManager.persist(legacy);
        entityManager.flush();
        entityManager.clear();

        assertThat(optionRepository.findAttributeBinding(new OptionGroupId(legacy.getId())))
                .contains(new OptionGroupAttributeBinding(new OptionGroupId(legacy.getId()), null));
        assertThat(optionRepository.findAttributeBinding(new OptionGroupId(UUID.randomUUID()))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dictionary", "product", "activation", "activation-after-deactivation"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void waiting_for_definition_does_not_hold_the_group_lock(String operation) throws Exception {
        var transaction = new TransactionTemplate(transactions);
        var fixture = transaction.execute(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            var category = category("Concurrent lock order", null);
            var definition = definition("test_lock_order_" + suffix, AttributeDataType.NUMBER);
            UUID attributeId = definition.getId().getValue();
            service.setPolicy(category.getId(), attributeId, new SetCategoryAttributeCommand(true, false, true, false, 0));
            var selected = service.createValue(attributeId,
                    new CreateAttributeValueCommand("16 GB", null, BigDecimal.valueOf(16), null, null));
            var additional = service.createValue(attributeId,
                    new CreateAttributeValueCommand("32 GB", null, BigDecimal.valueOf(32), null, null));
            UUID groupId = optionGroup(definition, "TEST_LOCK_ORDER_" + suffix);
            var added = optionService.addOptionValues(groupId, new AddOptionValuesCommand(List.of(
                    new AddOptionValueCommand(selected.getId().getValue(), "RAM16", "16 GB", 0))));
            if (operation.startsWith("activation")) {
                entityManager.flush();
                long version = detailsPort.find(new OptionGroupId(groupId)).orElseThrow().version();
                optionService.updateOptionGroup(groupId,
                        UpdateOptionGroupCommand.builder().status(OptionStatus.INACTIVE).build(), version);
            }
            return new LockFixture(category.getId(), attributeId, groupId, added.getFirst().id(), additional.getId().getValue());
        });
        var worker = Executors.newSingleThreadExecutor();
        String workerName = "option-lock-" + UUID.randomUUID();
        try (var parentConnection = dataSource.getConnection()) {
            parentConnection.setAutoCommit(false);
            try (var statement = parentConnection.prepareStatement("SELECT id FROM attribute_definitions WHERE id = ? FOR NO KEY UPDATE")) {
                statement.setObject(1, fixture.attributeId());
                statement.executeQuery().close();
            }
            var request = worker.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT set_config('application_name', ?, true)", String.class, workerName);
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                if (operation.equals("dictionary")) {
                    optionService.addOptionValues(fixture.groupId(), new AddOptionValuesCommand(List.of(
                            new AddOptionValueCommand(fixture.additionalCanonicalId(), "RAM32", "32 GB", 1))));
                } else if (operation.startsWith("activation")) {
                    long version = detailsPort.find(new OptionGroupId(fixture.groupId())).orElseThrow().version();
                    optionService.updateOptionGroup(fixture.groupId(),
                            UpdateOptionGroupCommand.builder().status(OptionStatus.ACTIVE).build(), version);
                } else {
                    var product = Product.builder().productId(new ProductId(UUID.randomUUID()))
                            .categoryId(new CategoryId(fixture.categoryId())).name("Product")
                            .description("Concurrent option policy validation test").brand("Brand")
                            .basePrice(new Money(BigDecimal.ONE)).conditionType(ConditionType.NEW)
                            .mainImageUrl("https://example.com/product.jpg").build();
                    productOptionPolicyValidator.validate(product, new OptionGroupId(fixture.groupId()),
                            List.of(AddProductOptionValueCommand.builder().optionValueId(fixture.selectedOptionId()).build()));
                }
            }));
            await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(25)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE application_name = ? AND wait_event_type = 'Lock'",
                            Integer.class, workerName)).isEqualTo(1));

            // A separate transaction can still lock the group while the request waits for its definition.
            try (var probe = dataSource.getConnection()) {
                probe.setAutoCommit(false);
                try (var statement = probe.prepareStatement("SELECT id FROM option_groups WHERE id = ? FOR UPDATE NOWAIT")) {
                    statement.setObject(1, fixture.groupId());
                    statement.executeQuery().close();
                }
                probe.rollback();
            }
            if (operation.equals("activation-after-deactivation")) {
                try (var statement = parentConnection.prepareStatement("UPDATE attribute_definitions SET status = 'INACTIVE' WHERE id = ?")) {
                    statement.setObject(1, fixture.attributeId());
                    statement.executeUpdate();
                }
                parentConnection.commit();
                assertThatThrownBy(() -> request.get(5, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(AttributeInactiveException.class);
                transaction.executeWithoutResult(status -> assertThat(
                        detailsPort.find(new OptionGroupId(fixture.groupId())).orElseThrow().status())
                        .isEqualTo(OptionStatus.INACTIVE));
            } else {
                parentConnection.commit();
                request.get(5, TimeUnit.SECONDS);
            }
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(6, TimeUnit.SECONDS)).isTrue();
            transaction.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM option_values WHERE option_group_id = ?", fixture.groupId());
                jdbc.update("DELETE FROM option_groups WHERE id = ?", fixture.groupId());
                jdbc.update("DELETE FROM category_attributes WHERE attribute_id = ?", fixture.attributeId());
                jdbc.update("DELETE FROM attribute_values WHERE attribute_id = ?", fixture.attributeId());
                jdbc.update("DELETE FROM attribute_definitions WHERE id = ?", fixture.attributeId());
                jdbc.update("DELETE FROM categories WHERE id = ?", fixture.categoryId());
            });
        }
    }

    private record LockFixture(Long categoryId, UUID attributeId, UUID groupId, UUID selectedOptionId, UUID additionalCanonicalId) {
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrent_information_edits_conflict_without_holding_a_pessimistic_lock() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        var binding = transaction.execute(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            var definition = definition("test_optimistic_" + suffix, AttributeDataType.NUMBER);
            UUID groupId = optionGroup(definition, "TEST_OPTIMISTIC_" + suffix);
            return new OptionGroupAttributeBinding(new OptionGroupId(groupId), definition.getId());
        });
        var staleRead = new CountDownLatch(1);
        var resumeStaleEdit = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        try {
            var staleEdit = worker.submit(() -> transaction.executeWithoutResult(status -> {
                var snapshot = detailsPort.find(binding.groupId()).orElseThrow();
                staleRead.countDown();
                try {
                    if (!resumeStaleEdit.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to resume stale edit");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Stale edit interrupted", exception);
                }
                detailsPort.update(snapshot.changeDisplayName("Stale edit"));
            }));
            assertThat(staleRead.await(5, TimeUnit.SECONDS)).isTrue();
            // This update must complete while the other transaction still holds its read snapshot.
            transaction.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '3s'");
                optionService.updateOptionGroup(binding.groupId().getValue(), UpdateOptionGroupCommand.builder()
                        .displayName("Fresh edit").status(OptionStatus.INACTIVE).build(), 0L);
            });
            resumeStaleEdit.countDown();
            assertThatThrownBy(() -> staleEdit.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(OptimisticLockingFailureException.class);
            transaction.executeWithoutResult(status -> {
                var current = detailsPort.find(binding.groupId()).orElseThrow();
                assertThat(current.displayName()).isEqualTo("Fresh edit");
                assertThat(current.status()).isEqualTo(OptionStatus.INACTIVE);
                assertThat(current.version()).isEqualTo(1);
                assertThatThrownBy(() -> optionService.updateOptionGroup(binding.groupId().getValue(),
                        UpdateOptionGroupCommand.builder().displayName("Another stale edit").build(), 0L))
                        .isInstanceOf(OptionGroupVersionConflictException.class);
                status.setRollbackOnly();
            });
        } finally {
            resumeStaleEdit.countDown();
            worker.shutdownNow();
            assertThat(worker.awaitTermination(6, TimeUnit.SECONDS)).isTrue();
            transaction.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM option_groups WHERE id = ?", binding.groupId().getValue());
                jdbc.update("DELETE FROM attribute_definitions WHERE id = ?", binding.attributeId().getValue());
            });
        }
    }

    @Test
    void inactive_definition_allows_information_edits_and_deactivation_but_rejects_reactivation() {
        var definition = definition("test_inactive_group_edit", AttributeDataType.NUMBER);
        UUID groupId = optionGroup(definition, "TEST_INACTIVE_GROUP_EDIT");
        var id = new OptionGroupId(groupId);
        entityManager.flush();
        entityManager.clear();
        service.deactivateDefinition(definition.getId().getValue());

        long initialVersion = detailsPort.find(id).orElseThrow().version();
        optionService.updateOptionGroup(groupId, UpdateOptionGroupCommand.builder()
                .displayName("Retired memory").status(OptionStatus.ACTIVE).build(), initialVersion);
        entityManager.flush();
        entityManager.clear();
        var renamed = detailsPort.find(id).orElseThrow();
        assertThat(renamed.displayName()).isEqualTo("Retired memory");

        optionService.updateOptionGroup(groupId,
                UpdateOptionGroupCommand.builder().status(OptionStatus.INACTIVE).build(), renamed.version());
        entityManager.flush();
        entityManager.clear();
        var inactive = detailsPort.find(id).orElseThrow();
        assertThat(inactive.status()).isEqualTo(OptionStatus.INACTIVE);

        assertThatThrownBy(() -> optionService.updateOptionGroup(groupId,
                UpdateOptionGroupCommand.builder().displayName("Rejected edit").status(OptionStatus.ACTIVE).build(), inactive.version()))
                .isInstanceOf(AttributeInactiveException.class);
        assertThat(detailsPort.find(id)).contains(inactive);
    }

    private UUID optionGroup(AttributeDefinition definition, String name) {
        return optionService.createOptionGroup(new CreateOptionGroupCommand(definition.getId().getValue(), name, name)).id();
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
    @Import({ProductDataAccessConfig.class, AttributeCatalogAdapter.class, AttributeValueQueryRepository.class,
            AttributeCatalogDataAccessMapper.class, AttributeCatalogService.class,
            OptionGroupRepositoryImpl.class, OptionGroupDataAccessMapper.class, OptionGroupAggregateMapper.class,
            OptionGroupDataMapper.class, OptionAttributeValidator.class, OptionGroupApplicationService.class, ProductOptionPolicyValidator.class,
            OptionGroupDetailsAdapter.class})
    static class Config {
        @Bean
        OptionGroupDomainService optionGroupDomainService(OptionGroupRepository repository) {
            return new OptionGroupDomainServiceImpl(repository);
        }

        @Bean
        IdGenerator idGenerator() {
            return Generators.timeBasedEpochGenerator()::generate;
        }
    }
}
