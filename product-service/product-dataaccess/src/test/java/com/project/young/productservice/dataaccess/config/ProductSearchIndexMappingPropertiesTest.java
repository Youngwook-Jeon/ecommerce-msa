package com.project.young.productservice.dataaccess.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchIndexMappingPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void missingSetting_usesBundledMapping() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var resource = context.getBean(ProductSearchIndexMappingProperties.class).resource();
            assertThat(resource.exists()).isTrue();
            assertThat(resource.getFilename()).isEqualTo("products-index.json");
        });
    }

    @Test
    void configuredFile_readsExternalMapping(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("custom-index.json");
        Files.writeString(file, "{\"mappings\":{\"dynamic\":\"strict\"}}");
        runner.withPropertyValues("product-service.search.mapping.resource=" + file.toUri()).run(context -> {
            assertThat(context).hasNotFailed();
            var resource = context.getBean(ProductSearchIndexMappingProperties.class).resource();
            try (var input = resource.getInputStream()) {
                assertThat(input.readAllBytes()).isEqualTo(Files.readAllBytes(file));
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProductSearchIndexMappingProperties.class)
    static class Config {
    }
}
