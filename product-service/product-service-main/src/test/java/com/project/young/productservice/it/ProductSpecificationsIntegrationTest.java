package com.project.young.productservice.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.ProductServiceMain;
import com.project.young.productservice.application.dto.command.AddOptionValueCommand;
import com.project.young.productservice.application.dto.command.AddOptionValuesCommand;
import com.project.young.productservice.application.dto.command.AddProductOptionGroupCommand;
import com.project.young.productservice.application.dto.command.AddProductOptionValueCommand;
import com.project.young.productservice.application.dto.command.AddProductVariantCommand;
import com.project.young.productservice.application.dto.command.AddProductVariantsCommand;
import com.project.young.productservice.application.dto.command.CreateAttributeDefinitionCommand;
import com.project.young.productservice.application.dto.command.CreateAttributeValueCommand;
import com.project.young.productservice.application.dto.command.CreateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.CreateProductCommand;
import com.project.young.productservice.application.dto.command.SetCategoryAttributeCommand;
import com.project.young.productservice.application.dto.command.SetProductSpecificationCommand;
import com.project.young.productservice.application.dto.command.UpdateProductStatusCommand;
import com.project.young.productservice.application.dto.command.UpdateProductVariantCommand;
import com.project.young.productservice.application.service.AttributeCatalogService;
import com.project.young.productservice.application.exception.ProductVersionConflictException;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.application.service.OptionGroupApplicationService;
import com.project.young.productservice.application.service.ProductApplicationService;
import com.project.young.productservice.application.service.ProductSpecificationService;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ProductServiceMain.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class ProductSpecificationsIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("specifications").withUsername("testuser").withPassword("testpass");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "&currentSchema=product");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired CategoryJpaRepository categories;
    @Autowired AttributeCatalogService catalog;
    @Autowired ProductApplicationService products;
    @Autowired ProductSpecificationService specifications;
    @Autowired OptionGroupApplicationService options;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;

    @BeforeEach
    void clear_products() {
        jdbc.execute("TRUNCATE TABLE categories, option_groups, products RESTART IDENTITY CASCADE");
        em.clear();
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void fixed_specifications_round_trip_and_use_product_versions_without_noop_events() throws Exception {
        var fixture = fixture(false);
        UUID product = draft(fixture.categoryId());
        set(product, fixture, fixture.valueId()).andExpect(status().isNoContent());
        em.flush(); em.clear();
        mvc.perform(get("/admin/products/{id}/specifications", product)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.specifications[0].attribute.id").value(fixture.attributeId().toString()))
                .andExpect(jsonPath("$.specifications[0].value.textValue").value("16"));
        long events = events(product);
        set(product, fixture, fixture.valueId()).andExpect(status().isNoContent());
        assertThat(version(product)).isEqualTo(2);
        assertThat(events(product)).isEqualTo(events);
        mvc.perform(put("/admin/products/{id}/specifications/{attribute}", product, fixture.attributeId())
                        .with(csrf()).param("expectedVersion", "1").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SetProductSpecificationCommand(fixture.valueId()))))
                .andExpect(status().isConflict());
        mvc.perform(delete("/admin/products/{id}/specifications/{attribute}", product, fixture.attributeId())
                        .with(csrf()).param("expectedVersion", Long.toString(version(product))))
                .andExpect(status().isNoContent());
        em.flush(); em.clear();
        mvc.perform(get("/admin/products/{id}/specifications", product)).andExpect(status().isOk())
                .andExpect(jsonPath("$.specifications").isEmpty());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void wrong_category_or_value_ownership_is_rejected_without_events() throws Exception {
        var fixture = fixture(false);
        var other = fixture(false);
        UUID product = draft(fixture.categoryId());
        long events = events(product);
        set(product, other, other.valueId()).andExpect(status().isBadRequest());
        set(product, fixture, other.valueId()).andExpect(status().isBadRequest());
        assertThat(version(product)).isEqualTo(1);
        assertThat(events(product)).isEqualTo(events);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_attributes WHERE product_id = ?", Long.class, product)).isZero();
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void fixed_and_purchase_option_representations_are_mutually_exclusive_in_both_directions() throws Exception {
        var fixture = fixture(false);
        var option = option(fixture);
        UUID fixedProduct = draft(fixture.categoryId());
        set(fixedProduct, fixture, fixture.valueId()).andExpect(status().isNoContent());
        long before = version(fixedProduct);
        mvc.perform(post("/admin/products/{id}/option-groups", fixedProduct).with(csrf())
                        .param("expectedVersion", Long.toString(before)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(option.command())))
                .andExpect(status().isBadRequest());
        assertThat(version(fixedProduct)).isEqualTo(before);
        UUID optionProduct = draft(fixture.categoryId());
        products.addProductOptionGroup(optionProduct, option.command(), version(optionProduct));
        set(optionProduct, fixture, fixture.valueId()).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_attributes WHERE product_id = ?", Long.class, optionProduct)).isZero();
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void activation_requires_attributes_and_active_required_fixed_values_cannot_be_removed() throws Exception {
        var fixture = fixture(true);
        UUID product = draft(fixture.categoryId());
        var added = products.addProductVariants(product, new AddProductVariantsCommand(List.of(
                AddProductVariantCommand.builder().stockQuantity(5).selectedProductOptionValueIds(Set.of()).build())), version(product));
        products.updateProductVariant(product, added.getFirst().productVariantId(), UpdateProductVariantCommand.builder()
                .status(ProductStatus.ACTIVE).stockQuantity(5).build(), version(product));
        long before = version(product);
        mvc.perform(patch("/admin/products/{id}/status", product).with(csrf()).param("expectedVersion", Long.toString(before))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(UpdateProductStatusCommand.builder().status(ProductStatus.ACTIVE).build())))
                .andExpect(status().isBadRequest());
        assertThat(version(product)).isEqualTo(before);
        set(product, fixture, fixture.valueId()).andExpect(status().isNoContent());
        products.updateProductStatus(product, UpdateProductStatusCommand.builder().status(ProductStatus.ACTIVE).build(), version(product));
        before = version(product);
        long events = events(product);
        mvc.perform(delete("/admin/products/{id}/specifications/{attribute}", product, fixture.attributeId())
                        .with(csrf()).param("expectedVersion", Long.toString(before)))
                .andExpect(status().isBadRequest());
        assertThat(version(product)).isEqualTo(before);
        assertThat(events(product)).isEqualTo(events);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void referenced_values_cannot_be_deleted_and_inactive_definitions_remain_readable() throws Exception {
        var fixture = fixture(false);
        UUID product = draft(fixture.categoryId());
        set(product, fixture, fixture.valueId()).andExpect(status().isNoContent());
        em.flush(); em.clear();
        mvc.perform(delete("/admin/attributes/{id}/values/{value}", fixture.attributeId(), fixture.valueId()).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTRIBUTE_IN_USE"));
        catalog.deactivateDefinition(fixture.attributeId());
        mvc.perform(get("/admin/products/{id}/specifications", product)).andExpect(status().isOk())
                .andExpect(jsonPath("$.specifications[0].attribute.status").value("INACTIVE"));
        UUID another = draft(fixture.categoryId());
        set(another, fixture, fixture.valueId()).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrent_fixed_and_option_writes_cannot_both_commit() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        var fixture = transaction.execute(status -> fixture(false));
        var option = transaction.execute(status -> option(fixture));
        UUID product = transaction.execute(status -> draft(fixture.categoryId()));
        long beforeEvents = events(product);
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var fixedWrite = workers.submit(() -> attempt(start, () -> specifications.setSpecification(product, fixture.attributeId(), new SetProductSpecificationCommand(fixture.valueId()), 1)));
            var optionWrite = workers.submit(() -> attempt(start, () -> products.addProductOptionGroup(product, option.command(), 1)));
            start.countDown();
            assertThat(List.of(fixedWrite.get(10, TimeUnit.SECONDS), optionWrite.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(version(product)).isEqualTo(2);
            assertThat(events(product)).isEqualTo(beforeEvents + 1);
            Long fixedCount = jdbc.queryForObject("SELECT count(*) FROM product_attributes WHERE product_id = ?", Long.class, product);
            Long optionCount = jdbc.queryForObject("SELECT count(*) FROM product_option_groups WHERE product_id = ?", Long.class, product);
            assertThat(fixedCount + optionCount).isEqualTo(1);
        } finally {
            start.countDown(); workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void a_policy_changed_while_waiting_for_definition_lock_is_not_read_from_stale_entity_state() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        var fixture = transaction.execute(status -> fixture(false));
        UUID product = transaction.execute(status -> draft(fixture.categoryId()));
        String workerName = "spec-policy-" + UUID.randomUUID();
        var worker = Executors.newSingleThreadExecutor();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT id FROM attribute_definitions WHERE id = ? FOR UPDATE")) {
                statement.setObject(1, fixture.attributeId());
                statement.executeQuery().close();
            }
            var request = worker.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT set_config('application_name', ?, true)", String.class, workerName);
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                specifications.setSpecification(product, fixture.attributeId(), new SetProductSpecificationCommand(fixture.valueId()), 1);
            }));
            await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(25)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE application_name = ? AND wait_event_type = 'Lock'",
                            Integer.class, workerName)).isEqualTo(1));
            try (var statement = connection.prepareStatement("UPDATE category_attributes SET enabled = false, required = false, option_allowed = false, filterable = false WHERE category_id = ? AND attribute_id = ?")) {
                statement.setLong(1, fixture.categoryId());
                statement.setObject(2, fixture.attributeId());
                statement.executeUpdate();
            }
            connection.commit();
            assertThatThrownBy(() -> request.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ProductDomainException.class);
            assertThat(version(product)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM product_attributes WHERE product_id = ?", Long.class, product)).isZero();
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean attempt(CountDownLatch start, Runnable action) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
            action.run();
            return true;
        } catch (ProductVersionConflictException | ProductDomainException rejected) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted);
        }
    }

    private ResultActions set(UUID product, Fixture fixture, UUID value) throws Exception {
        return mvc.perform(put("/admin/products/{id}/specifications/{attribute}", product, fixture.attributeId()).with(csrf())
                .param("expectedVersion", Long.toString(version(product))).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new SetProductSpecificationCommand(value))));
    }

    private Fixture fixture(boolean required) {
        var category = categories.saveAndFlush(CategoryEntity.builder().name("Specification category").status(CategoryStatusEntity.ACTIVE).build());
        var definition = catalog.createDefinition(new CreateAttributeDefinitionCommand("spec_" + UUID.randomUUID().toString().replace('-', '_'), "Memory", AttributeDataType.ENUM, null));
        var value = catalog.createValue(definition.getId().getValue(), new CreateAttributeValueCommand("16 GB", "16", null, null, null));
        catalog.setPolicy(category.getId(), definition.getId().getValue(), new SetCategoryAttributeCommand(true, required, true, true, 0));
        return new Fixture(category.getId(), definition.getId().getValue(), value.getId().getValue());
    }

    private OptionFixture option(Fixture fixture) {
        var group = options.createOptionGroup(new CreateOptionGroupCommand(fixture.attributeId(), "GROUP_" + UUID.randomUUID(), "Memory"));
        var values = options.addOptionValues(group.id(), new AddOptionValuesCommand(List.of(new AddOptionValueCommand(fixture.valueId(), "16", "16 GB", 0))));
        return new OptionFixture(AddProductOptionGroupCommand.builder().optionGroupId(group.id()).required(true)
                .optionValues(List.of(AddProductOptionValueCommand.builder().optionValueId(values.getFirst().id()).priceDelta(BigDecimal.ZERO).build())).build());
    }

    private UUID draft(Long categoryId) {
        return products.createProduct(CreateProductCommand.builder().name("Laptop").description("Laptop for specification integration tests")
                .brand("Brand").categoryId(categoryId).conditionType(ConditionType.NEW).basePrice(BigDecimal.TEN)
                .mainImageUrl("https://example.com/laptop.jpg").build()).id();
    }

    private long version(UUID product) {
        return jdbc.queryForObject("SELECT revision FROM product_catalog_versions WHERE product_id = ?", Long.class, product);
    }

    private long events(UUID product) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) em.flush();
        return jdbc.queryForObject("SELECT count(*) FROM product_catalog_outbox WHERE product_id = ?", Long.class, product);
    }

    private record Fixture(Long categoryId, UUID attributeId, UUID valueId) { }
    private record OptionFixture(AddProductOptionGroupCommand command) { }
}
