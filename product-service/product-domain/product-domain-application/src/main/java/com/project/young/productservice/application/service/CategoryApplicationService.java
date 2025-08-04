package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.*;
import com.project.young.productservice.application.mapper.CategoryDataMapper;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.exception.DuplicateCategoryNameException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.service.CategoryDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
public class CategoryApplicationService {

    private final CategoryRepository categoryRepository;
    private final CategoryDomainService categoryDomainService;
    private final CategoryDataMapper categoryDataMapper;

    public CategoryApplicationService(CategoryRepository categoryRepository,
                                      CategoryDomainService categoryDomainService,
                                      CategoryDataMapper categoryDataMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryDomainService = categoryDomainService;
        this.categoryDataMapper = categoryDataMapper;
    }

    @Transactional
    public CreateCategoryResponse createCategory(CreateCategoryCommand command) {
        log.info("Attempting to create category with name: {}", command.getName());

        if (!categoryDomainService.isCategoryNameUnique(command.getName())) {
            log.warn("Category name already exists: {}", command.getName());
            throw new DuplicateCategoryNameException("Category name '" + command.getName() + "' already exists.");
        }

        CategoryId parentCategoryId = Optional.ofNullable(command.getParentId())
                .map(CategoryId::new)
                .orElse(null);

        if (parentCategoryId != null) {
            Category parentCategory = categoryDomainService.validateParentCategory(parentCategoryId);

            if (!categoryDomainService.isParentDepthLessThanLimit(parentCategory.getId())) {
                log.warn("Cannot add category under parent {} due to depth limit.", parentCategoryId.getValue());
                throw new CategoryDomainException("Category depth limit exceeded.");
            }
        }

        Category newCategory = categoryDataMapper.toCategory(command, parentCategoryId);
        Category savedCategory = persistCategory(newCategory);

        // TODO: 도메인 이벤트 발행 준비 (향후 구현)

        log.info("Category saved successfully with id: {}", savedCategory.getId().getValue());
        return categoryDataMapper.toCreateCategoryResponse(savedCategory,
                "Category " + savedCategory.getName() + " created successfully.");
    }

    @Transactional
    public UpdateCategoryResponse updateCategory(Long categoryIdValue, UpdateCategoryCommand command) {
        validateUpdateRequest(categoryIdValue);

        CategoryId categoryId = new CategoryId(categoryIdValue);
        log.info("Attempting to update category with id: {}", categoryId.getValue());

        List<Category> categoriesToUpdate = findCategoriesForUpdate(categoryId, command.getStatus());

        if (categoriesToUpdate.isEmpty()) {
            throw new CategoryNotFoundException("Category with id " + categoryId.getValue() + " not found.");
        }

        Category mainCategory = findMainCategory(categoriesToUpdate, categoryId);
        validateCategoryCanBeUpdated(mainCategory);

        boolean hasChanges = performUpdates(mainCategory, categoriesToUpdate, command, categoryId);

        String message = finalizeUpdate(mainCategory, categoriesToUpdate, hasChanges);

        return categoryDataMapper.toUpdateCategoryResponse(mainCategory, message);
    }

    @Transactional
    public DeleteCategoryResponse deleteCategory(Long categoryIdValue) {
        validateDeleteRequest(categoryIdValue);

        CategoryId categoryId = new CategoryId(categoryIdValue);
        log.info("Attempting to soft-delete category and its subtree with root id: {}", categoryId.getValue());

        List<Category> categoriesToDelete = categoryDomainService.prepareForDeletion(categoryId);

        categoryRepository.saveAll(categoriesToDelete);
        scheduleOutboxEvent("CategoryDeleted", categoryId);

        Category rootCategory = categoriesToDelete.getFirst();
        log.info("{} categories (including sub-categories) marked as deleted.", categoriesToDelete.size());

        return new DeleteCategoryResponse(rootCategory.getId().getValue(),
                "Category " + rootCategory.getName() + " (ID: " + rootCategory.getId().getValue() + ") marked as deleted successfully.");
    }

    private void validateUpdateRequest(Long categoryIdValue) {
        if (categoryIdValue == null) {
            throw new IllegalArgumentException("Category ID for update cannot be null.");
        }
    }

    private void validateDeleteRequest(Long categoryIdValue) {
        if (categoryIdValue == null) {
            log.warn("Attempted to delete category with a null ID value.");
            throw new IllegalArgumentException("Category ID for delete cannot be null.");
        }
    }

    private List<Category> findCategoriesForUpdate(CategoryId categoryId, String newStatus) {
        Category mainCategory = categoryRepository.findById(categoryId).orElse(null);
        if (mainCategory == null) {
            return Collections.emptyList();
        }

        if (newStatus == null || mainCategory.getStatus().equals(newStatus)) {
            return List.of(mainCategory);
        }

        return switch (newStatus) {
            case Category.STATUS_ACTIVE -> categoryRepository.findAllAncestorsById(categoryId); // INACTIVE -> ACTIVE
            case Category.STATUS_INACTIVE -> categoryRepository.findSubTreeByIdAndStatusIn( // ACTIVE -> INACTIVE
                    categoryId, List.of(Category.STATUS_ACTIVE));
            default -> List.of(mainCategory);
        };
    }

    private Category findMainCategory(List<Category> categories, CategoryId categoryId) {
        return categories.stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Main category not found in the update list."));
    }

    private void validateCategoryCanBeUpdated(Category category) {
        if (category.isDeleted()) {
            throw new CategoryDomainException("Cannot update a category that has been deleted.");
        }
    }

    private boolean performUpdates(Category mainCategory, List<Category> categoriesToUpdate,
                                   UpdateCategoryCommand command, CategoryId categoryId) {
        boolean nameChanged = applyNameChange(mainCategory, command.getName(), categoryId);
        boolean parentChanged = applyParentChange(mainCategory, command.getParentId(), categoryId);
        boolean statusChanged = applyStatusChange(categoriesToUpdate, command.getStatus());

        return nameChanged || parentChanged || statusChanged;
    }

    private boolean applyNameChange(Category category, String newName, CategoryId categoryId) {
        if (newName != null && !Objects.equals(category.getName(), newName)) {
            if (!categoryDomainService.isCategoryNameUniqueForUpdate(newName, categoryId)) {
                throw new DuplicateCategoryNameException("Category name '" + newName + "' already exists.");
            }
            category.changeName(newName);
            return true;
        }
        return false;
    }

    private boolean applyParentChange(Category category, Long newParentIdValue, CategoryId categoryId) {
        CategoryId newParentId = (newParentIdValue != null) ? new CategoryId(newParentIdValue) : null;
        CategoryId currentParentId = category.getParentId().orElse(null);

        if (!Objects.equals(currentParentId, newParentId)) {
            log.info("Attempting to change parent for category id: {} from '{}' to '{}'",
                    categoryId.getValue(),
                    currentParentId != null ? currentParentId.getValue() : "null",
                    newParentId != null ? newParentId.getValue() : "null");

            categoryDomainService.validateParentChangeRules(categoryId, newParentId);

            category.changeParent(newParentId);
            return true;
        }
        return false;
    }

    private boolean applyStatusChange(List<Category> categories, String newStatus) {
        if (newStatus == null) {
            return false;
        }

        List<Category> categoriesThatNeedChange = categories.stream()
                .filter(cat -> !cat.isDeleted() && !Objects.equals(cat.getStatus(), newStatus))
                .toList();

        if (categoriesThatNeedChange.isEmpty()) {
            return false;
        }

        categoryDomainService.validateStatusChangeRules(categoriesThatNeedChange, newStatus);

        categoriesThatNeedChange.forEach(cat -> cat.changeStatus(newStatus));
        return true;
    }

    private String finalizeUpdate(Category mainCategory, List<Category> categoriesToUpdate, boolean hasChanges) {
        if (hasChanges) {
            categoryRepository.saveAll(categoriesToUpdate);
            scheduleOutboxEvent("CategoryUpdated", mainCategory.getId());
            log.info("{} categories updated for category id: {}",
                    categoriesToUpdate.size(), mainCategory.getId().getValue());
            return "Category '" + mainCategory.getName() + "' updated successfully.";
        } else {
            return "Category '" + mainCategory.getName() + "' was not changed.";
        }
    }

    private Category persistCategory(Category category) {
        Category savedCategory = categoryRepository.save(category);
        if (savedCategory.getId() == null) {
            log.error("Category ID was not assigned after save for name: {}", savedCategory.getName());
            throw new CategoryDomainException("Failed to assign ID to the new category.");
        }
        return savedCategory;
    }

    private void scheduleOutboxEvent(String eventType, CategoryId categoryId) {
        // TODO: 실제 아웃박스 패턴 구현
        log.info("TODO: Schedule {} event for category id: {}", eventType, categoryId.getValue());
    }
}