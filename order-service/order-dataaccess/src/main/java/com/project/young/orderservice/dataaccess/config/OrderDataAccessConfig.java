package com.project.young.orderservice.dataaccess.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.project.young.orderservice.dataaccess")
@EntityScan(basePackages = "com.project.young.orderservice.dataaccess")
public class OrderDataAccessConfig {
}
