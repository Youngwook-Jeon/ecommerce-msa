package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.command.CreateCategoryCommand;
import com.project.young.productservice.application.dto.command.UpdateCategoryCommand;
import com.project.young.productservice.application.dto.result.CreateCategoryResult;
import com.project.young.productservice.application.dto.result.DeleteCategoryResult;
import com.project.young.productservice.application.dto.result.UpdateCategoryResult;
import com.project.young.productservice.application.mapper.CategoryDataMapper;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.exception.DuplicateCategoryNameException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.service.CategoryDomainService;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
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
    public CreateCategoryResult createCategory(CreateCategoryCommand command) {
        log.info("Attempting to create category with name: {}", command.getName());

        if (!categoryDomainService.isCategoryNameUnique(command.getName())) {
            log.warn("Category name already exists: {}", command.getName());
            throw new DuplicateCategoryNameException("Category name '" + command.getName() + "' already exists.");
        }

        CategoryId parentCategoryId = Optional.ofNullable(command.getParentId())
                .map(CategoryId::new)
                .orElse(null);

        if (parentCategoryId != null) {
            Category parentCategory = categoryRepository.findById(parentCategoryId)
                    .orElseThrow(() -> new CategoryNotFoundException("Parent category with id " + parentCategoryId.getValue() + " not found."));
            categoryDomainService.validateParentCategory(parentCategory);

            if (!categoryDomainService.isParentDepthLessThanLimit(parentCategory.getId())) {
                log.warn("Cannot add category under parent {} due to depth limit.", parentCategoryId.getValue());
                throw new CategoryDomainException("Category depth limit exceeded.");
            }
        }

        Category newCategory = categoryDataMapper.toCategory(command, parentCategoryId);
        Category savedCategory = persistCategory(newCategory);

        log.info("Category saved successfully with id: {}", savedCategory.getId().getValue());

        return categoryDataMapper.toCreateCategoryResult(savedCategory);
    }

    @Transactional
    public UpdateCategoryResult updateCategory(Long categoryIdValue, UpdateCategoryCommand command) {
        validateUpdateRequest(categoryIdValue, command);

        CategoryId categoryId = new CategoryId(categoryIdValue);
        log.info("Attempting to update category with id: {}", categoryId.getValue());

        Category mainCategory = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category with id " + categoryId.getValue() + " not found."));

        validateCategoryCanBeUpdated(mainCategory);

        boolean nameOrParentChanged = applyNameChange(mainCategory, command.getName(), mainCategory.getId());
        nameOrParentChanged |= applyParentChange(mainCategory, command.getParentId(), mainCategory.getId());

        CategoryStatus oldStatus = mainCategory.getStatus();
        CategoryStatus newStatus = command.getStatus();
        boolean statusChanged = oldStatus != newStatus;
        boolean persistedByUpdateAll = false;

        if (statusChanged) {
            List<Category> categoriesToUpdate = resolveAffectedCategories(mainCategory.getId(), newStatus);

            if (!categoriesToUpdate.isEmpty()) {
                categoryDomainService.validateStatusChangeRules(categoriesToUpdate, newStatus);
                categoriesToUpdate.forEach(category -> category.changeStatus(newStatus));
                mainCategory.changeStatus(newStatus);

                List<Category> categoriesToPersist = categoriesToUpdate.stream()
                        .map(category -> category.getId().equals(mainCategory.getId()) ? mainCategory : category)
                        .toList();
                categoryRepository.updateAll(categoriesToPersist);
                persistedByUpdateAll = true;
                log.info("{} categories updated via updateAll for status change.", categoriesToUpdate.size());
            }
        }

        if (nameOrParentChanged || (statusChanged && !persistedByUpdateAll)) {
            if (statusChanged) {
                mainCategory.changeStatus(newStatus);
            }
            categoryRepository.update(mainCategory);
        }

        return categoryDataMapper.toUpdateCategoryResult(mainCategory);
    }

    @Transactional
    public DeleteCategoryResult deleteCategory(Long categoryIdValue) {
        validateDeleteRequest(categoryIdValue);

        CategoryId categoryId = new CategoryId(categoryIdValue);
        log.info("Attempting to soft-delete category and its subtree with root id: {}", categoryId.getValue());

        List<Category> categoriesToDelete = categoryRepository.findSubTreeByIdAndStatusIn(
                categoryId,
                List.of(CategoryStatus.ACTIVE, CategoryStatus.INACTIVE)
        );
        if (categoriesToDelete.isEmpty()) {
            throw new CategoryNotFoundException("Category with id " + categoryId.getValue() + " not found.");
        }
        categoryDomainService.validateDeletionRules(categoriesToDelete);
        categoriesToDelete.forEach(Category::markAsDeleted);

        categoryRepository.updateAll(categoriesToDelete);

        Category rootCategory = categoriesToDelete.getFirst();

        log.info("{} categories (including sub-categories) marked as deleted.", categoriesToDelete.size());

        return new DeleteCategoryResult(rootCategory.getId().getValue(), rootCategory.getName());
    }

    private void validateUpdateRequest(Long categoryIdValue, UpdateCategoryCommand command) {
        if (categoryIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid update request.");
        }
    }

    private void validateDeleteRequest(Long categoryIdValue) {
        if (categoryIdValue == null) {
            log.warn("Attempted to delete category with a null ID value.");
            throw new IllegalArgumentException("Category ID for delete cannot be null.");
        }
    }

    private void validateCategoryCanBeUpdated(Category category) {
        if (category.isDeleted()) {
            throw new CategoryDomainException("Cannot update a category that has been deleted.");
        }
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

    private boolean applyParentChange(Category category, Long newParentIdValue, CategoryId selfId) {
        CategoryId newParentId = (newParentIdValue != null) ? new CategoryId(newParentIdValue) : null;
        CategoryId currentParentId = category.getParentId().orElse(null);

        if (Objects.equals(currentParentId, newParentId)) {
            return false;
        }

        log.info("Attempting to change parent for category id: {} from '{}' to '{}'",
                selfId.getValue(),
                currentParentId != null ? currentParentId.getValue() : "null",
                newParentId != null ? newParentId.getValue() : "null");
        if (newParentId == null) {
            category.changeParent(null);
            return true;
        }

        Category newParent = categoryRepository.findById(newParentId)
                .orElseThrow(() -> new CategoryNotFoundException("Parent category with id " + newParentId.getValue() + " not found."));
        categoryDomainService.validateParentChangeRules(selfId, newParentId, newParent);
        category.changeParent(newParentId);
        return true;
    }

    private List<Category> resolveAffectedCategories(CategoryId categoryId, CategoryStatus newStatus) {
        if (newStatus == CategoryStatus.ACTIVE) {
            return categoryRepository.findAllAncestorsById(categoryId);
        }
        if (newStatus == CategoryStatus.INACTIVE) {
            return categoryRepository.findSubTreeByIdAndStatusIn(categoryId, List.of(CategoryStatus.ACTIVE));
        }
        throw new IllegalArgumentException("Invalid status: " + newStatus);
    }

    private Category persistCategory(Category category) {
        Category savedCategory = categoryRepository.insert(category);
        if (savedCategory.getId() == null) {
            log.error("Category ID was not assigned after save for name: {}", savedCategory.getName());
            throw new CategoryDomainException("Failed to assign ID to the new category.");
        }
        return savedCategory;
    }

}