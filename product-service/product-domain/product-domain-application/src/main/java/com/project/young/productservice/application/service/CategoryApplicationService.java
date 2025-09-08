package com.project.young.productservice.application.service;

import com.project.young.common.domain.event.publisher.DomainEventPublisher;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.*;
import com.project.young.productservice.application.mapper.CategoryDataMapper;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.event.CategoryDeletedEvent;
import com.project.young.productservice.domain.event.CategoryStatusChangedEvent;
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
    private final DomainEventPublisher domainEventPublisher;

    public CategoryApplicationService(CategoryRepository categoryRepository,
                                      CategoryDomainService categoryDomainService,
                                      CategoryDataMapper categoryDataMapper,
                                      DomainEventPublisher domainEventPublisher) {
        this.categoryRepository = categoryRepository;
        this.categoryDomainService = categoryDomainService;
        this.categoryDataMapper = categoryDataMapper;
        this.domainEventPublisher = domainEventPublisher;
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

        log.info("Category saved successfully with id: {}", savedCategory.getId().getValue());
        return categoryDataMapper.toCreateCategoryResponse(savedCategory,
                "Category " + savedCategory.getName() + " created successfully.");
    }

    @Transactional
    public UpdateCategoryResponse updateCategory(Long categoryIdValue, UpdateCategoryCommand command) {
        validateUpdateRequest(categoryIdValue);

        CategoryId categoryId = new CategoryId(categoryIdValue);
        log.info("Attempting to update category with id: {}", categoryId.getValue());

        Category mainCategory = categoryRepository.findById(new CategoryId(categoryIdValue))
                .orElseThrow(() -> new CategoryNotFoundException("Category with id " + categoryId.getValue() + " not found."));
        validateCategoryCanBeUpdated(mainCategory);

        boolean nameOrParentChanged = applyNameChange(mainCategory, command.getName(), mainCategory.getId());
        nameOrParentChanged |= applyParentChange(mainCategory, command.getParentId(), mainCategory.getId());

        // 상태 변경은 Bulk Update로 별도 처리
        String oldStatus = mainCategory.getStatus();
        String newStatus = command.getStatus();
        boolean statusChanged = false;

        if (newStatus != null && !oldStatus.equals(newStatus)) {
            statusChanged = true;
            List<CategoryId> idsToUpdateStatus;

            if (Category.STATUS_ACTIVE.equals(newStatus)) { // INACTIVE -> ACTIVE
                idsToUpdateStatus = categoryRepository.findAllAncestorsById(mainCategory.getId())
                        .stream().map(Category::getId).toList();
            } else { // ACTIVE -> INACTIVE
                idsToUpdateStatus = categoryRepository.findSubTreeByIdAndStatusIn(mainCategory.getId(), List.of(Category.STATUS_ACTIVE))
                        .stream().map(Category::getId).toList();
            }

            // 비즈니스 규칙 검증
            if (!idsToUpdateStatus.isEmpty()) {
                List<Category> categoriesToValidate = categoryRepository.findAllById(idsToUpdateStatus);
                categoryDomainService.validateStatusChangeRules(categoriesToValidate, newStatus);

                categoryRepository.updateStatusForIds(newStatus, idsToUpdateStatus);
                log.info("{} categories' status updated via bulk operation.", idsToUpdateStatus.size());
            }
        }

        String message;
        if (nameOrParentChanged || statusChanged) {
            if (statusChanged) mainCategory.changeStatus(newStatus);
            categoryRepository.save(mainCategory);

            message = "Category '" + mainCategory.getName() + "' updated successfully.";
            // TODO: Outbox 로직
        } else {
            message = "Category '" + mainCategory.getName() + "' was not changed.";
        }

        return categoryDataMapper.toUpdateCategoryResponse(mainCategory, message);
    }

    @Transactional
    public DeleteCategoryResponse deleteCategory(Long categoryIdValue) {
        validateDeleteRequest(categoryIdValue);

        CategoryId categoryId = new CategoryId(categoryIdValue);
        log.info("Attempting to soft-delete category and its subtree with root id: {}", categoryId.getValue());

        List<Category> categoriesToDelete = categoryDomainService.prepareForDeletion(categoryId);

        categoryRepository.saveAll(categoriesToDelete);

        Category rootCategory = categoriesToDelete.getFirst();
        List<CategoryId> deletedCategoryIds = categoriesToDelete.stream()
                .map(Category::getId)
                .toList();

        CategoryDeletedEvent deletionEvent = new CategoryDeletedEvent(
                categoryId,
                rootCategory.getName(),
                deletedCategoryIds
        );
        domainEventPublisher.publishEventAfterCommit(deletionEvent);

        for (Category category : categoriesToDelete) {
            CategoryStatusChangedEvent statusEvent = new CategoryStatusChangedEvent(
                    category.getId(),
                    category.getStatus(),
                    Category.STATUS_DELETED
            );
            domainEventPublisher.publishEventAfterCommit(statusEvent);
        }

        log.info("{} categories (including sub-categories) marked as deleted.", categoriesToDelete.size());

        return new DeleteCategoryResponse(rootCategory.getId().getValue(),
                "Category " + rootCategory.getName() + " (ID: " + rootCategory.getId().getValue() + ") marked as deleted successfully.");
    }

    @Transactional
    public void updateCategoriesStatus(List<CategoryId> categoryIds, String newStatus) {
        log.info("Updating status for {} categories to: {}", categoryIds.size(), newStatus);

        List<Category> categories = categoryRepository.findAllById(categoryIds);

        if (categories.size() != categoryIds.size()) {
            throw new IllegalArgumentException("Some categories not found");
        }

        // 현재 상태 기록
        List<StatusChangeRecord> statusChanges = categories.stream()
                .map(category -> new StatusChangeRecord(category.getId(), category.getStatus(), newStatus))
                .filter(record -> !record.oldStatus().equals(record.newStatus()))
                .toList();

        if (statusChanges.isEmpty()) {
            log.info("No categories need status update - all are already in status: {}", newStatus);
            return;
        }

        categoryDomainService.validateStatusChangeRules(categories, newStatus);
        categoryDomainService.processStatusChange(categories, newStatus);

        categoryRepository.saveAll(categories);

        statusChanges.forEach(change -> {
            CategoryStatusChangedEvent event = new CategoryStatusChangedEvent(
                    change.categoryId(), change.oldStatus(), change.newStatus()
            );
            domainEventPublisher.publishEventAfterCommit(event);
        });

        log.info("Successfully updated status for {} categories and published {} events",
                categories.size(), statusChanges.size());
    }

    private void publishStatusChangeEvents(List<CategoryId> categoryIds, String oldStatus, String newStatus) {
        categoryIds.forEach(categoryId -> {
            CategoryStatusChangedEvent event = new CategoryStatusChangedEvent(
                    categoryId, oldStatus, newStatus
            );
            domainEventPublisher.publishEventAfterCommit(event);
            log.debug("Scheduled status change event for category: {}", categoryId.getValue());
        });
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

    private record StatusChangeRecord(CategoryId categoryId, String oldStatus, String newStatus) {}
}