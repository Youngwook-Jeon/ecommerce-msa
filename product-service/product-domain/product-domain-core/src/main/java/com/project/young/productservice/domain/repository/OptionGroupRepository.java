package com.project.young.productservice.domain.repository;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.valueobject.OptionGroupAttributeBinding;

import java.util.Optional;

public interface OptionGroupRepository {

    void insert(OptionGroup optionGroup);

    void update(OptionGroup optionGroup);

    Optional<OptionGroup> findById(OptionGroupId optionGroupId);

    /** Reads scalar IDs without a row lock or loading a group entity. */
    Optional<OptionGroupAttributeBinding> findAttributeBinding(OptionGroupId optionGroupId);

    /**
     * Locks the group before explicitly fetching its values. If a definition lock is also required,
     * acquire that lock first; then revalidate the binding after acquiring this group lock.
     * For multiple parents/groups, acquire all required definition locks before group locks,
     * sorting IDs within each type. Group-only operations may skip the definition level.
     */
    Optional<OptionGroup> findByIdForUpdate(OptionGroupId optionGroupId);

    boolean existsByName(String name);
}
