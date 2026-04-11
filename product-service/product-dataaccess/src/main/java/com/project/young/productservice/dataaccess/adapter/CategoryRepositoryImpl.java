package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.mapper.CategoryAggregateMapper;
import com.project.young.productservice.dataaccess.mapper.CategoryDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.valueobject.CategoryStatus;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryJpaRepository categoryJpaRepository;
    private final CategoryDataAccessMapper categoryDataAccessMapper;
    private final CategoryAggregateMapper categoryAggregateMapper;
    private final EntityManager entityManager;

    public CategoryRepositoryImpl(CategoryJpaRepository categoryJpaRepository,
                                  CategoryDataAccessMapper categoryDataAccessMapper,
                                  CategoryAggregateMapper categoryAggregateMapper,
                                  EntityManager entityManager) {
        this.categoryJpaRepository = categoryJpaRepository;
        this.categoryDataAccessMapper = categoryDataAccessMapper;
        this.categoryAggregateMapper = categoryAggregateMapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public Category insert(Category category) {
        if (category == null) {
            throw new IllegalArgumentException("category must not be null.");
        }

        final CategoryEntity parentRef = category.getParentId().isPresent()
                ? categoryJpaRepository.getReferenceById(category.getParentId().get().getValue())
                : null;
        CategoryEntity toPersist = categoryDataAccessMapper.categoryToCategoryEntity(category, parentRef);
        entityManager.persist(toPersist);
        return categoryAggregateMapper.toCategory(toPersist);
    }

    @Override
    @Transactional
    public void update(Category category) {
        if (category == null) {
            throw new IllegalArgumentException("category must not be null.");
        }
        if (category.getId() == null) {
            throw new IllegalArgumentException("category id must not be null for update.");
        }

        final CategoryEntity parentRef = category.getParentId().isPresent()
                ? categoryJpaRepository.getReferenceById(category.getParentId().get().getValue())
                : null;
        final Long id = category.getId().getValue();
        CategoryEntity current = categoryJpaRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + id));
        current.setName(category.getName());
        current.setStatus(categoryDataAccessMapper.toEntityStatus(category.getStatus()));
        current.setParent(parentRef);
        // Let dirty checking flush changes on commit.
    }

    @Override
    @Transactional
    public void updateAll(List<Category> categories) {
        if (categories == null) {
            throw new IllegalArgumentException("categories must not be null.");
        }
        if (categories.isEmpty()) {
            return;
        }
        if (categories.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("categories must not contain null elements.");
        }

        List<Long> existingIds = categories.stream()
                .filter(c -> c.getId() != null)
                .map(c -> c.getId().getValue())
                .collect(Collectors.toList());

        Map<Long, CategoryEntity> existingEntitiesMap = categoryJpaRepository.findAllById(existingIds).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, entity -> entity));

        categories.forEach(domainCategory -> {
            if (domainCategory.getId() == null) {
                throw new IllegalArgumentException("category id must not be null for updateAll.");
            }
            CategoryEntity entity = existingEntitiesMap.get(domainCategory.getId().getValue());
            if (entity == null) {
                throw new CategoryNotFoundException("Category with id " + domainCategory.getId().getValue() + " not found for update in updateAll.");
            }
            categoryDataAccessMapper.updateEntityFromDomain(domainCategory, entity);

            if (domainCategory.getParentId().isPresent()) {
                CategoryEntity parentRef = categoryJpaRepository.getReferenceById(domainCategory.getParentId().get().getValue());
                entity.setParent(parentRef);
            } else {
                entity.setParent(null);
            }

        });
    }


    @Override
    public boolean existsByName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank.");
        }
        return categoryJpaRepository.existsByName(name);
    }

    @Override
    public boolean existsById(CategoryId categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("CategoryId object can not be null.");
        }
        return categoryJpaRepository.existsById(categoryId.getValue());
    }

    @Override
    public boolean existsByNameAndIdNot(String name, CategoryId categoryIdToExclude) {
        if (name == null || name.isBlank() || categoryIdToExclude == null) {
            throw new IllegalArgumentException("Not valid category.");
        }
        return categoryJpaRepository.existsByNameAndIdNot(name, categoryIdToExclude.getValue());
    }

    @Override
    public Optional<Category> findById(CategoryId categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("CategoryId object can not be null.");
        }
        return categoryJpaRepository.findById(categoryId.getValue())
                .map(categoryAggregateMapper::toCategory);
    }

    @Override
    public List<Category> findAllById(List<CategoryId> categoryIdList) {
        if (categoryIdList == null) {
            throw new IllegalArgumentException("categoryIdList must not be null.");
        }
        if (categoryIdList.isEmpty()) {
            return Collections.emptyList();
        }
        if (categoryIdList.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("categoryIdList must not contain null elements.");
        }

        List<Long> ids = categoryIdList.stream()
                .map(CategoryId::getValue)
                .collect(Collectors.toList());

        List<CategoryEntity> foundEntities = categoryJpaRepository.findAllById(ids);

        return foundEntities.stream()
                .map(categoryAggregateMapper::toCategory)
                .collect(Collectors.toList());
    }

    @Override
    public List<Category> findAllSubTreeById(CategoryId categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId must not be null.");
        }

        List<CategoryEntity> subTreeEntities = categoryJpaRepository.findSubTreeByIdNative(categoryId.getValue());

        return subTreeEntities.stream()
                .map(categoryAggregateMapper::toCategory)
                .collect(Collectors.toList());
    }

    @Override
    public List<Category> findSubTreeByIdAndStatusIn(CategoryId categoryId, List<CategoryStatus> statusList) {
        if (categoryId == null || statusList == null) {
            throw new IllegalArgumentException("categoryId or statusList must not be null.");
        }
        if (statusList.isEmpty()) {
            return Collections.emptyList();
        }

        String[] statusNames = statusList.stream()
                .map(Enum::name)
                .toArray(String[]::new);

        List<CategoryEntity> subTreeEntities =
                categoryJpaRepository.findSubTreeByIdAndStatusInNative(categoryId.getValue(), statusNames);

        return subTreeEntities.stream()
                .map(categoryAggregateMapper::toCategory)
                .collect(Collectors.toList());
    }

    @Override
    public List<Category> findAllAncestorsById(CategoryId categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId must not be null.");
        }

        List<CategoryEntity> ancestorEntities = categoryJpaRepository.findAncestorsByIdNative(categoryId.getValue());

        return ancestorEntities.stream()
                .map(categoryAggregateMapper::toCategory)
                .collect(Collectors.toList());
    }

    @Override
    public int getDepth(CategoryId categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId must not be null.");
        }

        Integer depth = categoryJpaRepository.getDepthByIdNative(categoryId.getValue());
        if (depth == null) {
            throw new CategoryNotFoundException("Category with id " + categoryId.getValue() + " not found.");
        }
        return depth;
    }

    @Override
    public int getMaxSubtreeDepthByIdAndStatusIn(CategoryId categoryId, List<CategoryStatus> statusList) {
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId must not be null.");
        }
        if (statusList.isEmpty()) {
            throw new IllegalArgumentException("statusList must not be empty.");
        }

        String[] statusNames = statusList.stream()
                .map(Enum::name)
                .toArray(String[]::new);

        Integer depth = categoryJpaRepository.getMaxSubtreeDepthByIdAndStatusInNative(categoryId.getValue(), statusNames);
        return (depth != null) ? depth : 0;
    }

}
