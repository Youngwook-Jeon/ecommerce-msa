package com.project.young.productservice;

import com.project.young.productservice.domain.ProductDomainService;
import com.project.young.productservice.domain.ProductDomainServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    @Bean
    public ProductDomainService productDomainService() {
        return new ProductDomainServiceImpl();
    }
}
