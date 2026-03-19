package com.project.young.productservice.domain.service;

import com.project.young.productservice.domain.exception.OptionDomainException;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OptionGroupDomainServiceImpl implements OptionGroupDomainService {

    private final OptionGroupRepository optionGroupRepository;

    public OptionGroupDomainServiceImpl(OptionGroupRepository optionGroupRepository) {
        this.optionGroupRepository = optionGroupRepository;
    }

    @Override
    public boolean isValidOptionGroupName(String name) {
        if (name == null) {
            throw new OptionDomainException("Option group name must not be null.");
        }
        return !optionGroupRepository.existsByName(name);
    }
}
