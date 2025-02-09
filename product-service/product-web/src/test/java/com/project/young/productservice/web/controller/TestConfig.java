package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.domain.ports.input.service.ProductApplicationService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;

import static org.mockito.Mockito.mock;

@TestConfiguration
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

    @Bean
    public ProductApplicationService productApplicationService() {
        return mock(ProductApplicationService.class);
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // 임의의 secret key로 JwtDecoder 생성
        return NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec("dummysecret".getBytes(), "HmacSHA256")).build();
    }

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
