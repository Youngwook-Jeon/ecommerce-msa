package com.project.young.productservice.application.config;

import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.service.OptionGroupDomainService;
import com.project.young.productservice.domain.service.OptionGroupDomainServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class OptionServiceBeanConfig {

    @Bean
    public OptionGroupDomainService optionGroupDomainService(OptionGroupRepository optionGroupRepository) {
        return new OptionGroupDomainServiceImpl(optionGroupRepository);
    }
}
