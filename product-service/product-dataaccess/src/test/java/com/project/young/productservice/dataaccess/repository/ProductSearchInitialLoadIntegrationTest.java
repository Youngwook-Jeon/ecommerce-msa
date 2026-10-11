package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.mapper.OpenSearchProductDocumentMapper;
import com.project.young.productservice.dataaccess.search.OpenSearchProductBulkWriter;
import com.project.young.productservice.dataaccess.search.OpenSearchProductDocument;
import com.project.young.productservice.dataaccess.search.ProductSearchIndexBatchReader;
import com.project.young.productservice.dataaccess.search.ProductSearchInitialLoader;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = ProductSearchInitialLoadIntegrationTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductSearchInitialLoadIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb").withUsername("testuser").withPassword("testpass");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ProductJpaRepository repository;
    @Autowired private ProductSearchInitialLoader loader;
    @Autowired private OpenSearchProductBulkWriter writer;

    @Test
    void loadsAllStatusesInPostgresUuidOrderAndBulkRunsOutsideEvenAnOuterTransaction() {
        var transaction = new TransactionTemplate(transactionManager);
        var ids = List.of(UUID.fromString("7fffffff-0000-0000-0000-000000000001"),
                UUID.fromString("80000000-0000-0000-0000-000000000001"),
                UUID.fromString("ffffffff-0000-0000-0000-000000000001"));
        transaction.executeWithoutResult(s -> {
            repository.deleteAll();
            entityManager.flush();
            entityManager.clear();
            var category = CategoryEntity.builder().name("이어폰").status(CategoryStatusEntity.INACTIVE)
                    .createdAt(Instant.now()).updatedAt(Instant.now()).build();
            entityManager.persist(category);
            for (int i = 0; i < ids.size(); i++) {
                entityManager.persist(ProductEntity.builder().id(ids.get(i)).category(category).name("상품 " + i)
                        .brand(" Example ").basePrice(new BigDecimal("10.00")).mainImageUrl("https://example.com/image")
                        .conditionType(ConditionTypeEntity.NEW).status(i == 0 ? ProductStatusEntity.ACTIVE : ProductStatusEntity.DELETED)
                        .createdAt(Instant.now()).updatedAt(Instant.now()).build());
            }
        });
        transaction.executeWithoutResult(s -> {
            var first = repository.findFirstSearchIndexBatch(PageRequest.of(0, 2));
            assertThat(first).extracting(ProductEntity::getId).containsExactly(ids.get(0), ids.get(1));
            assertThat(Hibernate.isInitialized(first.getFirst().getCategory())).isTrue();
            var next = repository.findNextSearchIndexBatch(ids.get(1), PageRequest.of(0, 2));
            assertThat(next).extracting(ProductEntity::getId).containsExactly(ids.get(2));
        });
        List<OpenSearchProductDocument> written = new ArrayList<>();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(writer).verifyTarget(anyString());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            written.addAll(invocation.<List<OpenSearchProductDocument>>getArgument(1));
            return null;
        }).when(writer).write(anyString(), anyList());
        transaction.executeWithoutResult(s -> {
            assertThat(loader.load("products-v1", 2)).isEqualTo(new ProductSearchInitialLoader.Result(3, 2));
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });
        assertThat(written).extracting(OpenSearchProductDocument::id).containsExactlyElementsOf(ids.stream().map(UUID::toString).toList());
        assertThat(written).allSatisfy(d -> {
            assertThat(d.categoryName()).isEqualTo("이어폰");
            assertThat(d.categoryStatus()).isEqualTo("INACTIVE");
            assertThat(d.brandKey()).isEqualTo("example");
        });
        assertThat(written.getLast().productStatus()).isEqualTo("DELETED");
    }

    @Configuration
    @Import({ProductDataAccessConfig.class, ProductSearchIndexBatchReader.class, OpenSearchProductDocumentMapper.class})
    static class Config {
        @Bean
        OpenSearchProductBulkWriter writer() {
            return mock(OpenSearchProductBulkWriter.class);
        }

        @Bean
        ProductSearchInitialLoader loader(ProductSearchIndexBatchReader reader, OpenSearchProductBulkWriter writer) {
            return new ProductSearchInitialLoader(reader, writer);
        }
    }
}
