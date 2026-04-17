package com.project.young.productservice.application.config;

import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.domain.service.ProductDomainService;
import com.project.young.productservice.domain.service.ProductDomainServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductServiceBeanConfig {

    @Bean
    public ProductDomainService productDomainService(ProductRepository productRepository,
                                                     CategoryRepository categoryRepository,
                                                     OptionGroupRepository optionGroupRepository) {
        return new ProductDomainServiceImpl(productRepository, categoryRepository, optionGroupRepository);
    }
}
