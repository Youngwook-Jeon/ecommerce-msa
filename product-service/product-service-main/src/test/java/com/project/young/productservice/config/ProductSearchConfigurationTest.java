package com.project.young.productservice.config;

import com.project.young.productservice.application.dto.query.ProductSearchQuery;
import com.project.young.productservice.application.exception.ProductSearchUnavailableException;
import com.project.young.productservice.application.port.output.ProductSearchPort;
import com.project.young.productservice.application.service.ProductSearchQueryValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.dataaccess.config.OpenSearchProductConfiguration;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexBuildConfiguration;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildWorkPort;
import com.project.young.productservice.application.service.ProductSearchIndexBuildExecutor;
import com.project.young.productservice.dataaccess.search.ProductSearchIndexVerificationReader;
import com.project.young.productservice.dataaccess.search.CrossFieldsProductQueryBuilder;
import com.project.young.productservice.dataaccess.search.OpenSearchProductQueryBuilder;
import com.project.young.productservice.dataaccess.search.OpenSearchProductSearchAdapter;
import com.project.young.productservice.dataaccess.search.ProductSearchIndexBatchReader;
import org.junit.jupiter.api.Test;
import com.project.young.productservice.dataaccess.search.ProductSearchSynchronizationReader;
import com.project.young.productservice.dataaccess.search.ProductSearchSynchronizationTargetReader;
import com.project.young.productservice.dataaccess.search.ProductSearchCategoryNameReader;
import com.project.young.productservice.application.port.output.ProductSearchIndexReconciliationPort;
import com.project.young.productservice.application.service.ProductSearchIndexActivationReconciler;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexReconciliationConfiguration;
import com.project.young.productservice.dataaccess.search.ProductSearchIndexReconciliationScheduler;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.service.ProductSearchIndexActivationExecutor;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexActivationConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ProductSearchConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("product-service.search.enabled=false")
            .withUserConfiguration(ProductSearchConfiguration.class);

    @Test
    void missingAdapterFailsExplicitlyInsteadOfReturningEmptyResults() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ProductSearchPort.class);
            var criteria = new ProductSearchQueryValidator().validate(
                    new ProductSearchQuery(null, null, null, null, null, null, null, null));
            assertThatThrownBy(() -> context.getBean(ProductSearchPort.class).search(criteria))
                    .isInstanceOf(ProductSearchUnavailableException.class);
        });
    }

    @Test
    void configuredAdapterReplacesUnavailablePort() {
        ProductSearchPort adapter = criteria -> {
            throw new AssertionError("Not invoked in this configuration test");
        };
        runner.withBean("configuredSearchAdapter", ProductSearchPort.class, () -> adapter).run(context -> {
            assertThat(context).hasSingleBean(ProductSearchPort.class);
            assertThat(context.getBean(ProductSearchPort.class)).isSameAs(adapter);
        });
    }

    @Test
    void enabledSearchRegistersExactlyOneRealAdapter() {
        new ApplicationContextRunner().withUserConfiguration(ProductSearchConfiguration.class, OpenSearchProductConfiguration.class)
                .withBean(ProductSearchCategoryNameReader.class, () -> mock(ProductSearchCategoryNameReader.class))
                .withBean(ProductSearchSynchronizationReader.class, () -> mock(ProductSearchSynchronizationReader.class))
                .withBean(ProductSearchSynchronizationTargetReader.class, () -> mock(ProductSearchSynchronizationTargetReader.class))
                .withBean(ProductSearchIndexBatchReader.class, () -> mock(ProductSearchIndexBatchReader.class))
                .withBean(ObjectMapper.class, ObjectMapper::new).run(context -> {
                    assertThat(context).hasSingleBean(ProductSearchPort.class);
                    assertThat(context.getBean(ProductSearchPort.class)).isInstanceOf(OpenSearchProductSearchAdapter.class);
                    assertThat(context).hasSingleBean(OpenSearchProductQueryBuilder.class);
                    assertThat(context.getBean(OpenSearchProductQueryBuilder.class)).isInstanceOf(CrossFieldsProductQueryBuilder.class);
                });
    }

    @Test
    void customQueryBuilderReplacesDefaultBean() {
        OpenSearchProductQueryBuilder customBuilder = new CrossFieldsProductQueryBuilder();
        new ApplicationContextRunner().withUserConfiguration(ProductSearchConfiguration.class, OpenSearchProductConfiguration.class)
                .withBean(ProductSearchCategoryNameReader.class, () -> mock(ProductSearchCategoryNameReader.class))
                .withBean(ProductSearchSynchronizationReader.class, () -> mock(ProductSearchSynchronizationReader.class))
                .withBean(ProductSearchSynchronizationTargetReader.class, () -> mock(ProductSearchSynchronizationTargetReader.class))
                .withBean(ProductSearchIndexBatchReader.class, () -> mock(ProductSearchIndexBatchReader.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean("customQueryBuilder", OpenSearchProductQueryBuilder.class, () -> customBuilder)
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenSearchProductQueryBuilder.class);
                    assertThat(context.getBean(OpenSearchProductQueryBuilder.class)).isSameAs(customBuilder);
                    assertThat(context).hasSingleBean(ProductSearchPort.class);
                });
    }

    @Test
    void disabledSearchDoesNotCreateClient() {
        runner.withUserConfiguration(OpenSearchProductConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(ProductSearchPort.class);
            assertThat(context).doesNotHaveBean("productSearchClient");
        });
    }    @Test
    void enabledWorker_registersExecutorWithDefaultIntervals() {
        new ApplicationContextRunner().withUserConfiguration(OpenSearchProductConfiguration.class, ProductSearchIndexBuildConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ProductSearchCategoryNameReader.class, () -> mock(ProductSearchCategoryNameReader.class))
                .withBean(ProductSearchSynchronizationReader.class, () -> mock(ProductSearchSynchronizationReader.class))
                .withBean(ProductSearchSynchronizationTargetReader.class, () -> mock(ProductSearchSynchronizationTargetReader.class))
                .withBean(ProductSearchIndexBatchReader.class, () -> mock(ProductSearchIndexBatchReader.class))
                .withBean(ProductSearchIndexVerificationReader.class, () -> mock(ProductSearchIndexVerificationReader.class))
                .withBean(ProductSearchIndexBuildWorkPort.class, () -> mock(ProductSearchIndexBuildWorkPort.class))
                .run(context -> assertThat(context).hasSingleBean(ProductSearchIndexBuildExecutor.class));
    }

    @Test
    void disabledWorker_doesNotRegisterExecutor() {
        new ApplicationContextRunner().withUserConfiguration(ProductSearchIndexBuildConfiguration.class)
                .withPropertyValues("product-service.search.worker.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ProductSearchIndexBuildExecutor.class));
    }


    @Test
    void enabledWorkers_registerSeparateExecutorsAndHeartbeats() {
        new ApplicationContextRunner().withUserConfiguration(OpenSearchProductConfiguration.class,
                        ProductSearchIndexBuildConfiguration.class, ProductSearchIndexActivationConfiguration.class, ProductSearchIndexReconciliationConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ProductSearchCategoryNameReader.class, () -> mock(ProductSearchCategoryNameReader.class))
                .withBean(ProductSearchSynchronizationReader.class, () -> mock(ProductSearchSynchronizationReader.class))
                .withBean(ProductSearchSynchronizationTargetReader.class, () -> mock(ProductSearchSynchronizationTargetReader.class))
                .withBean(ProductSearchIndexBatchReader.class, () -> mock(ProductSearchIndexBatchReader.class))
                .withBean(ProductSearchIndexVerificationReader.class, () -> mock(ProductSearchIndexVerificationReader.class))
                .withBean(ProductSearchIndexBuildWorkPort.class, () -> mock(ProductSearchIndexBuildWorkPort.class))
                .withBean(ProductSearchIndexActivationWorkPort.class, () -> mock(ProductSearchIndexActivationWorkPort.class))
                .withBean(ProductSearchIndexReconciliationPort.class, () -> mock(ProductSearchIndexReconciliationPort.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProductSearchIndexActivationExecutor.class);
                    assertThat(context).hasSingleBean(ProductSearchIndexBuildExecutor.class);
                    assertThat(context).hasSingleBean(ProductSearchIndexActivationReconciler.class);
                    assertThat(context).hasSingleBean(ProductSearchIndexReconciliationScheduler.class);
                });
    }

    @Test
    void disabledSearch_aliasManagementReturnsExplicitUnavailable() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ProductSearchIndexAliasPort.class);
            assertThatThrownBy(() -> context.getBean(ProductSearchIndexAliasPort.class).readAlias("products-read"))
                    .isInstanceOf(com.project.young.productservice.application.exception.ProductSearchIndexManagementException.class);
        });
    }

    @Test
    void disabledAutomaticReconciliation_keepsManualRecheckAvailable() {
        runner.withUserConfiguration(ProductSearchIndexReconciliationConfiguration.class)
                .withPropertyValues("product-service.search.reconciliation.enabled=false")
                .withBean(ProductSearchIndexActivationWorkPort.class, () -> mock(ProductSearchIndexActivationWorkPort.class))
                .withBean(ProductSearchIndexReconciliationPort.class, () -> mock(ProductSearchIndexReconciliationPort.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProductSearchIndexActivationReconciler.class);
                    assertThat(context).doesNotHaveBean(ProductSearchIndexReconciliationScheduler.class);
                });
    }
}
