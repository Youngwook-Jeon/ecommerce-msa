package com.project.young.productservice.application.config;

import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.service.CategoryDomainService;
import com.project.young.productservice.domain.service.CategoryDomainServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CategoryServiceBeanConfig {

    @Bean
    public CategoryDomainService categoryDomainService(CategoryRepository categoryRepository) {
        return new CategoryDomainServiceImpl(categoryRepository);
    }
}
