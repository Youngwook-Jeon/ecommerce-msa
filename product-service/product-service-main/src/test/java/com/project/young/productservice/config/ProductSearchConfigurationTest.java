package com.project.young.productservice.config;

import com.project.young.productservice.application.dto.query.ProductSearchQuery;
import com.project.young.productservice.application.exception.ProductSearchUnavailableException;
import com.project.young.productservice.application.port.output.ProductSearchPort;
import com.project.young.productservice.application.service.ProductSearchQueryValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.dataaccess.config.OpenSearchProductConfiguration;
import com.project.young.productservice.dataaccess.search.CrossFieldsProductQueryBuilder;
import com.project.young.productservice.dataaccess.search.OpenSearchProductQueryBuilder;
import com.project.young.productservice.dataaccess.search.OpenSearchProductSearchAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    }
}
