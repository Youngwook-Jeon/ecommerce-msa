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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

        Optional<Long> parentIdRawOpt = Optional.ofNullable(command.getParentId());
        if (!categoryDomainService.isCategoryNameUnique(command.getName())) {
            log.warn("Category name already exists: {}", command.getName());
            throw new DuplicateCategoryNameException("Category name '" + command.getName() + "' already exists.");
        }

        CategoryId parentCategoryIdVo = null;
        if (parentIdRawOpt.isPresent()) {
            parentCategoryIdVo = new CategoryId(parentIdRawOpt.get());
            if (!categoryRepository.existsById(parentCategoryIdVo)) {
                log.warn("Parent category not found for id: {}", parentCategoryIdVo.getValue());
                throw new CategoryDomainException("Parent category with id " + parentCategoryIdVo.getValue() + " not found.");
            }
            if (!categoryDomainService.isParentDepthLessThanLimit(parentCategoryIdVo)) {
                log.warn("Cannot add category under parent {} due to depth limit.", parentCategoryIdVo.getValue());
                throw new CategoryDomainException("Category depth limit exceeded.");
            }
        }

        Category newCategory = categoryDataMapper.toCategory(command, parentCategoryIdVo);

        Category savedCategory = categoryRepository.save(newCategory);
        if (savedCategory.getId() == null) {
            log.error("Category ID was not assigned after save for name: {}", savedCategory.getName());
            throw new CategoryDomainException("Failed to assign ID to the new category.");
        }
        log.info("Category saved successfully with id: {}", savedCategory.getId().getValue());

//        CategoryCreatedEvent event = new CategoryCreatedEvent(
//                savedCategory.getId(),
//                savedCategory.getName(),
//                savedCategory.getParentId().orElse(null),
//                ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
//        );

        // TODO: Persist the 'event' object to the dedicated outbox table here.
        // Example of what might go here:
        // OutboxEvent outboxEvent = mapToOutboxEvent(event);
        // outboxRepository.save(outboxEvent);
        log.info("TODO: Outbox persistence logic for CategoryCreatedEvent (id: {}) needs to be implemented here.", savedCategory.getId().getValue());

        return categoryDataMapper.toCreateCategoryResponse(savedCategory,
                "Category " + savedCategory.getName() + " created successfully.");
    }

    @Transactional
    public UpdateCategoryResponse updateCategory(Long categoryIdValue, UpdateCategoryCommand command) {
        if (categoryIdValue == null) {
            log.warn("Attempted to update category with a null ID value.");
            throw new IllegalArgumentException("Category ID for update cannot be null.");
        }
        CategoryId categoryIdToUpdate = new CategoryId(categoryIdValue);
        log.info("Attempting to update category with id: {}", categoryIdToUpdate.getValue());

        Category category = categoryRepository.findById(categoryIdToUpdate)
                .orElseThrow(() -> {
                    log.warn("Category not found for id: {}", categoryIdToUpdate.getValue());
                    return new CategoryNotFoundException("Category with id " + categoryIdToUpdate.getValue() + " not found.");
                });

        boolean updated = false;

        String newName = command.getName();
        if (newName != null && !Objects.equals(category.getName(), newName)) {
            log.info("Attempting to change name for category id: {} from '{}' to '{}'",
                    categoryIdToUpdate.getValue(), category.getName(), newName);
            if (!categoryDomainService.isCategoryNameUniqueForUpdate(newName, categoryIdToUpdate)) {
                log.warn("New category name '{}' already exists (excluding self).", newName);
                throw new DuplicateCategoryNameException("Category name '" + newName + "' already exists.");
            }
            category.changeName(newName);
            updated = true;
        }

        Optional<Long> newParentIdRawOpt = Optional.ofNullable(command.getParentId());
        CategoryId newParentIdVoForUpdate = newParentIdRawOpt.map(CategoryId::new).orElse(null);
        CategoryId currentParentIdVo = category.getParentId().orElse(null);

        if (!Objects.equals(currentParentIdVo, newParentIdVoForUpdate)) {
            log.info("Attempting to change parent for category id: {} from '{}' to '{}'",
                    categoryIdToUpdate.getValue(),
                    currentParentIdVo != null ? currentParentIdVo.getValue() : "null",
                    newParentIdVoForUpdate != null ? newParentIdVoForUpdate.getValue() : "null");

            if (newParentIdVoForUpdate != null) {
                if (!categoryRepository.existsById(newParentIdVoForUpdate)) {
                    log.warn("New parent category not found for id: {}", newParentIdVoForUpdate.getValue());
                    throw new CategoryDomainException("New parent category with id " + newParentIdVoForUpdate.getValue() + " not found.");
                }
                if (!categoryDomainService.isParentDepthLessThanLimit(newParentIdVoForUpdate)) {
                    log.warn("Cannot move category under new parent {} due to depth limit.", newParentIdVoForUpdate.getValue());
                    throw new CategoryDomainException("Category depth limit exceeded with new parent.");
                }
            }
            category.changeParent(newParentIdVoForUpdate);
            updated = true;
        }

        Category savedCategory;
        String message;

        if (updated) {
            savedCategory = categoryRepository.save(category);
            log.info("Category id: {} updated successfully.", savedCategory.getId().getValue());
            message = "Category " + savedCategory.getName() + " updated successfully.";

            log.info("TODO: Outbox persistence logic for CategoryUpdatedEvent (id: {}) needs to be implemented here.", savedCategory.getId().getValue());
        } else {
            savedCategory = category;
            message = "Category " + savedCategory.getName() + " was not changed (no new values provided).";
            log.info("No actual changes detected for category id: {}. Update skipped.", categoryIdToUpdate.getValue());
        }

        return categoryDataMapper.toUpdateCategoryResponse(savedCategory, message);
    }

    @Transactional
    public DeleteCategoryResponse deleteCategory(Long categoryIdValue) {
        if (categoryIdValue == null) {
            log.warn("Attempted to delete category with a null ID value.");
            throw new IllegalArgumentException("Category ID for delete cannot be null.");
        }
        CategoryId categoryIdToDelete = new CategoryId(categoryIdValue);
        log.info("Attempting to soft-delete category and its subtree with root id: {}", categoryIdToDelete.getValue());

        List<Category> categoriesToDelete = categoryRepository.findAllSubTreeById(categoryIdToDelete);

        if (categoriesToDelete.isEmpty()) {
            log.warn("Category not found for id: {}, cannot delete.", categoryIdToDelete.getValue());
            throw new CategoryNotFoundException("Category with id " + categoryIdToDelete.getValue() + " not found, cannot delete.");
        }

        Category rootCategory = categoriesToDelete.stream()
                .filter(c -> c.getId().equals(categoryIdToDelete))
                .findFirst()
                .orElse(categoriesToDelete.getFirst());

        categoriesToDelete.forEach(Category::markAsDeleted);

        List<Category> savedEntities = categoryRepository.saveAll(categoriesToDelete);
        log.info("{} categories were marked as deleted.", savedEntities.size());

        return new DeleteCategoryResponse(rootCategory.getId().getValue(),
                "Category " + rootCategory.getName() + " (ID: " + rootCategory.getId().getValue() + ") marked as deleted successfully.");
    }
}
