package com.project.young.productservice.dataaccess.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.project.young.productservice.dataaccess")
@EntityScan(basePackages = "com.project.young.productservice.dataaccess")
public class ProductDataAccessConfig {
}
