package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.CreateCategoryResponse;
import com.project.young.productservice.application.mapper.CategoryDataMapper;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.DuplicateCategoryNameException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.service.CategoryDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        Optional<Long> parentId = Optional.ofNullable(command.getParentId());
        if (!categoryDomainService.isCategoryNameUnique(command.getName())) {
            log.warn("Category name already exists: {}", command.getName());
            throw new DuplicateCategoryNameException("Category name '" + command.getName() + "' already exists.");
        }

        CategoryId parentCategoryId = null;
        if (parentId.isPresent()) {
            parentCategoryId = new CategoryId(parentId.get());
            if (!categoryRepository.existsById(parentCategoryId)) {
                log.warn("Parent category not found for id: {}", parentCategoryId.getValue());
                throw new CategoryDomainException("Parent category with id " + parentCategoryId.getValue() + " not found.");
            }
            if (!categoryDomainService.isParentDepthLessThanLimit(parentCategoryId)) {
                log.warn("Cannot add category under parent {} due to depth limit.", parentCategoryId.getValue());
                throw new CategoryDomainException("Category depth limit exceeded.");
            }
        }

        Category newCategory = categoryDataMapper.toCategory(command, parentCategoryId);

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
}
