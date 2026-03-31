package com.project.young.productservice.domain.repository;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.domain.entity.OptionGroup;

import java.util.Optional;

public interface OptionGroupRepository {

    OptionGroup insert(OptionGroup optionGroup);

    OptionGroup update(OptionGroup optionGroup);

    Optional<OptionGroup> findById(OptionGroupId optionGroupId);

    boolean existsByName(String name);
}