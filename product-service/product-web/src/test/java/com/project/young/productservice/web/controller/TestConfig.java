package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "com.project.young.productservice.web")
public class TestConfig {
    // 필요한 빈 설정들
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    // ValidationFactory 설정 (Bean Validation을 위해)
    @Bean
    public ValidatorFactory validatorFactory() {
        return Validation.buildDefaultValidatorFactory();
    }

    @Bean
    public Validator validator() {
        return validatorFactory().getValidator();
    }

    // 만약 ProductApplicationService를 사용한다면
//    @Bean
//    public ProductApplicationService productApplicationService() {
//        return mock(ProductApplicationService.class);
//    }

    // MessageSource 설정 (validation 메시지를 위해)
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource
                = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }
}
