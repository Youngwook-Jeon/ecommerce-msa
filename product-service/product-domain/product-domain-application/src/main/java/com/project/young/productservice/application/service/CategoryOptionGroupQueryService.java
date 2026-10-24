package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.result.CategoryOptionGroupValuesResult;
import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.application.port.output.CategoryOptionGroupReadRepository;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionValueView;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.exception.OptionDomainException;
import com.project.young.productservice.domain.exception.OptionGroupNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CategoryOptionGroupQueryService {
    public static final int MAX_OPTION_GROUPS_PER_REQUEST = 10;

    private final AttributeCatalogPort catalog;
    private final CategoryOptionGroupReadRepository optionGroups;

    public CategoryOptionGroupQueryService(AttributeCatalogPort catalog, CategoryOptionGroupReadRepository optionGroups) {
        this.catalog = catalog;
        this.optionGroups = optionGroups;
    }

    public List<ReadCategoryOptionGroupView> getAvailableOptionGroups(Long categoryId) {
        validateCategory(categoryId);
        return optionGroups.findAvailableByCategoryId(categoryId);
    }

    public List<CategoryOptionGroupValuesResult> getAvailableOptionValues(Long categoryId, List<UUID> groupIds) {
        Set<UUID> requestedIds = validateGroupIds(groupIds);
        validateCategory(categoryId);
        var groups = optionGroups.findAvailableByCategoryIdAndIds(categoryId, requestedIds);
        var foundIds = groups.stream().map(ReadCategoryOptionGroupView::id).collect(Collectors.toSet());
        if (!foundIds.equals(requestedIds)) {
            throw new OptionGroupNotFoundException("One or more option groups are unavailable for this category");
        }
        var valuesByGroup = optionGroups.findActiveValuesByGroupIds(requestedIds).stream()
                .collect(Collectors.groupingBy(ReadCategoryOptionValueView::optionGroupId));
        return groups.stream().map(group -> new CategoryOptionGroupValuesResult(group,
                valuesByGroup.getOrDefault(group.id(), List.of()))).toList();
    }

    private Set<UUID> validateGroupIds(List<UUID> groupIds) {
        if (groupIds == null || groupIds.isEmpty() || groupIds.size() > MAX_OPTION_GROUPS_PER_REQUEST
                || groupIds.stream().anyMatch(Objects::isNull)) {
            throw new OptionDomainException("Provide between 1 and " + MAX_OPTION_GROUPS_PER_REQUEST + " non-null option group IDs");
        }
        return Set.copyOf(groupIds);
    }

    private void validateCategory(Long categoryId) {
        if (categoryId == null || categoryId < 1) {
            throw new CategoryDomainException("Category ID must be positive");
        }
        catalog.findCategory(categoryId).filter(category -> !category.deleted())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));
    }
}
