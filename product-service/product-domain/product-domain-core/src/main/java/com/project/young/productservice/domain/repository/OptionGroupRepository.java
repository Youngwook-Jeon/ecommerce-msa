package com.project.young.productservice.domain.repository;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.valueobject.OptionGroupAttributeBinding;

import java.util.Optional;
import java.util.List;
import java.util.Set;

public interface OptionGroupRepository {

    void insert(OptionGroup optionGroup);

    void update(OptionGroup optionGroup);

    Optional<OptionGroup> findById(OptionGroupId optionGroupId);

    /** Reads scalar IDs without a row lock or loading a group entity. */
    Optional<OptionGroupAttributeBinding> findAttributeBinding(OptionGroupId optionGroupId);

    /** Batch scalar lookup; does not load managed group entities or acquire row locks. */
    List<OptionGroupAttributeBinding> findAttributeBindings(Set<OptionGroupId> optionGroupIds);

    /**
     * Locks all existing parents in ascending ID order, then explicitly loads all their values.
     * Acquire every required attribute-definition lock before calling this method.
     * Missing IDs are omitted; callers must check completeness and revalidate bindings.
     */
    List<OptionGroup> findAllByIdsForUpdate(Set<OptionGroupId> optionGroupIds);

    /**
     * Locks the group before explicitly fetching its values. If a definition lock is also required,
     * acquire that lock first; then revalidate the binding after acquiring this group lock.
     * For multiple parents/groups, acquire all required definition locks before group locks,
     * sorting IDs within each type. Group-only operations may skip the definition level.
     */
    Optional<OptionGroup> findByIdForUpdate(OptionGroupId optionGroupId);

    boolean existsByName(String name);
}
