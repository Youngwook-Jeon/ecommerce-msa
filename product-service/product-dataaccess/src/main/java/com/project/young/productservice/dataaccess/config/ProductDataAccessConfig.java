package com.project.young.productservice.dataaccess.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.project.young.productservice.dataaccess.catalog.ProductCatalogRevisionStore;
import org.springframework.context.annotation.Import;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Import(ProductCatalogRevisionStore.class)
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.project.young.productservice.dataaccess")
@EntityScan(basePackages = "com.project.young.productservice.dataaccess")
@EnableConfigurationProperties({PublicProductSearchProperties.class, ProductSearchIndexMappingProperties.class})
public class ProductDataAccessConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
