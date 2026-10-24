package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionValueView;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CategoryOptionGroupReadRepository {
    /** Returns available groups from this category's explicit policies, without loading option values. */
    List<ReadCategoryOptionGroupView> findAvailableByCategoryId(Long categoryId);

    List<ReadCategoryOptionGroupView> findAvailableByCategoryIdAndIds(Long categoryId, Set<UUID> groupIds);

    /** Returns active linked values for groups already validated as available in the same read snapshot. */
    List<ReadCategoryOptionValueView> findActiveValuesByGroupIds(Set<UUID> groupIds);
}
