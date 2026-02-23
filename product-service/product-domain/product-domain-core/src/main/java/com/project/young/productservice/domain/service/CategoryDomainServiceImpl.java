package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class CategoryDomainServiceImpl implements CategoryDomainService {

    private static final int MAX_CATEGORY_DEPTH = 5; // TODO: 설정으로 외부화 가능

    private final CategoryRepository categoryRepository;

    public CategoryDomainServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public boolean isCategoryNameUnique(String name) {
        return !categoryRepository.existsByName(name);
    }

    @Override
    public boolean isParentDepthLessThanLimit(CategoryId parentId) {
        if (parentId == null) {
            return true;
        }

        int parentDepth = calculateCategoryDepth(parentId);
        return parentDepth < MAX_CATEGORY_DEPTH;
    }

    @Override
    public boolean isCategoryNameUniqueForUpdate(String name, CategoryId categoryIdToExclude) {
        return !categoryRepository.existsByNameAndIdNot(name, categoryIdToExclude);
    }

    @Override
    public Category validateParentCategory(CategoryId parentId) {
        if (parentId == null) {
            return null;
        }

        Category parentCategory = categoryRepository.findById(parentId)
                .orElseThrow(() ->
                        new CategoryDomainException("Parent category with id " + parentId.getValue() + " not found.")
                );

        if (!parentCategory.getStatus().isActive()) {
            throw new CategoryDomainException("A category can only be created/moved under an 'ACTIVE' parent category. " +
                    "Parent status is: " + parentCategory.getStatus());
        }

        return parentCategory;
    }

    @Override
    public void validateParentChangeRules(CategoryId categoryId, CategoryId newParentId) {
        if (newParentId == null) {
            return; // 루트로 이동하는 것은 항상 허용
        }

        validateParentCategory(newParentId);

        // 자기 자신을 부모로 설정하는 것 방지 (Category 엔티티에서도 검증하지만 추가 보장)
        if (categoryId.equals(newParentId)) {
            throw new CategoryDomainException("A category cannot be set as its own parent.");
        }

        // 순환 참조 검증
        validateCircularReference(categoryId, newParentId);

        // 깊이 제한: (최상위 카테고리부터 새 부모까지의 depth) + (옮길 서브트리 높이) <= MAX
        int newParentDepthFromTopLevel = categoryRepository.getDepth(newParentId);
        int subtreeHeight = categoryRepository.getMaxSubtreeDepthByIdAndStatusIn(categoryId, List.of(CategoryStatus.ACTIVE, CategoryStatus.INACTIVE)); // 자기 자신 포함 높이(leaf면 1)

        int newMaxDepthAfterMove = newParentDepthFromTopLevel + subtreeHeight;
        if (newMaxDepthAfterMove > MAX_CATEGORY_DEPTH) {
            log.warn("Cannot move category {} under new parent {} due to depth limit. parentDepth={}, subtreeHeight={}, max={}",
                    categoryId.getValue(), newParentId.getValue(), newParentDepthFromTopLevel, subtreeHeight, MAX_CATEGORY_DEPTH);
            throw new CategoryDomainException("Category depth limit exceeded with new parent.");
        }
    }

    @Override
    public void validateStatusChangeRules(List<Category> categories, CategoryStatus newStatus) {
        if (newStatus == null) {
            return;
        }

        for (Category category : categories) {
            if (category.isDeleted()) {
                throw new CategoryDomainException("Cannot change status of a deleted category: " + category.getId().getValue());
            }

            if (category.getStatus() == newStatus) {
                continue;
            }

            // 상태 전환 가능성 검증
            if (!category.getStatus().canTransitionTo(newStatus)) {
                throw new CategoryDomainException(
                        String.format("Invalid status transition from %s to %s for category %s",
                                category.getStatus(), newStatus, category.getId().getValue())
                );
            }
        }
    }

    @Override
    public List<Category> prepareForDeletion(CategoryId categoryId) {
        List<Category> categoriesToDelete = categoryRepository.findSubTreeByIdAndStatusIn(
                categoryId,
                List.of(CategoryStatus.ACTIVE, CategoryStatus.INACTIVE)
        );

        if (categoriesToDelete.isEmpty()) {
            throw new CategoryNotFoundException("Category with id " + categoryId.getValue() + " not found.");
        }

        validateDeletionRules(categoriesToDelete);

        categoriesToDelete.forEach(Category::markAsDeleted);
        log.info("Prepared {} categories for deletion", categoriesToDelete.size());

        return categoriesToDelete;
    }

    @Override
    public List<CategoryId> getAffectedCategories(CategoryId categoryId, CategoryStatus newStatus) {
        List<CategoryId> idsToUpdateStatus;
        if (newStatus == CategoryStatus.ACTIVE) { // INACTIVE -> ACTIVE
            idsToUpdateStatus = categoryRepository.findAllAncestorsById(categoryId)
                    .stream().map(Category::getId).toList();
        } else if (newStatus == CategoryStatus.INACTIVE) { // ACTIVE -> INACTIVE
            idsToUpdateStatus = categoryRepository.findSubTreeByIdAndStatusIn(categoryId, List.of(CategoryStatus.ACTIVE))
                    .stream().map(Category::getId).toList();
        } else {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }
        return idsToUpdateStatus;
    }

    private void validateCircularReference(CategoryId categoryId, CategoryId newParentId) {
        List<Category> descendants = categoryRepository.findSubTreeByIdAndStatusIn(
                categoryId,
                List.of(CategoryStatus.ACTIVE, CategoryStatus.INACTIVE)
        );

        boolean isCircularReference = descendants.stream()
                .anyMatch(descendant -> descendant.getId().equals(newParentId));

        if (isCircularReference) {
            throw new CategoryDomainException("Circular reference detected: new parent is a descendant of the category.");
        }
    }

    private void validateDeletionRules(List<Category> categoriesToDelete) {
        // TODO: 삭제될 카테고리들에 활성 상품이 있는지 검증
        // 예: productRepository.countByCategoryIdInAndStatus(categoryIds, "ACTIVE") > 0 이면 예외 발생
        log.debug("Validating deletion rules for {} categories", categoriesToDelete.size());

        // 추가 비즈니스 규칙 예시:
        // - 하위 카테고리에 활성 상품이 있으면 삭제 불가
        // - 특정 시스템 카테고리는 삭제 불가
        // - 최근 생성된 카테고리는 일정 기간 삭제 불가 등
    }

    private int calculateCategoryDepth(CategoryId categoryId) {
        return categoryRepository.getDepth(categoryId);
    }
}