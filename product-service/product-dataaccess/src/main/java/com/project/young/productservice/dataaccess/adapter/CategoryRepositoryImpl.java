package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.mapper.CategoryDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.repository.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryJpaRepository categoryJpaRepository;
    private final CategoryDataAccessMapper categoryDataAccessMapper;

    public CategoryRepositoryImpl(CategoryJpaRepository categoryJpaRepository, CategoryDataAccessMapper categoryDataAccessMapper) {
        this.categoryJpaRepository = categoryJpaRepository;
        this.categoryDataAccessMapper = categoryDataAccessMapper;
    }

    @Override
    @Transactional
    public Category save(Category category) {
        final CategoryEntity parentRef = category.getParentId().isPresent()
                ? categoryJpaRepository.getReferenceById(category.getParentId().get().getValue())
                : null;

        CategoryEntity toSave;
        if (category.getId() != null) {
            final Long id = category.getId().getValue();
            CategoryEntity current = categoryJpaRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
            current.setName(category.getName());
            current.setStatus(category.getStatus());
            current.setParent(parentRef);
            toSave = current;
        } else {
            toSave = categoryDataAccessMapper.categoryToCategoryEntity(category, parentRef);
        }

        CategoryEntity saved = categoryJpaRepository.save(toSave);
        return categoryDataAccessMapper.categoryEntityToCategory(saved);
    }

    @Override
    @Transactional
    public List<Category> saveAll(List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }

        // 업데이트가 필요한 기존 엔티티들의 ID를 수집
        List<Long> existingIds = categories.stream()
                .filter(c -> c.getId() != null)
                .map(c -> c.getId().getValue())
                .collect(Collectors.toList());

        Map<Long, CategoryEntity> existingEntitiesMap = categoryJpaRepository.findAllById(existingIds).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, entity -> entity));

        List<CategoryEntity> entitiesToPersist = categories.stream().map(domainCategory -> {
            CategoryEntity entity;

            if (domainCategory.getId() != null) {
                // === UPDATE ===
                // (DB 조회 없음)
                entity = existingEntitiesMap.get(domainCategory.getId().getValue());
                if (entity == null) {
                    // saveAll 목록에는 있었지만 DB에는 없는 경우에 대한 예외 처리
                    throw new EntityNotFoundException("Category with id " + domainCategory.getId().getValue() + " not found for update in saveAll.");
                }
                // 매퍼를 사용해 속성을 업데이트 (부모 관계는 아래에서 별도 처리)
                categoryDataAccessMapper.updateEntityFromDomain(domainCategory, entity);

            } else {
                // === CREATE ===
                // 매퍼를 사용해 새 엔티티를 구성
                entity = categoryDataAccessMapper.categoryToCategoryEntity(domainCategory, null);
            }

            // 부모 관계는 getReferenceById를 사용해 공통으로 처리
            if (domainCategory.getParentId().isPresent()) {
                CategoryEntity parentRef = categoryJpaRepository.getReferenceById(domainCategory.getParentId().get().getValue());
                entity.setParent(parentRef);
            } else {
                entity.setParent(null);
            }

            return entity;
        }).collect(Collectors.toList());

        List<CategoryEntity> savedEntities = categoryJpaRepository.saveAll(entitiesToPersist);

        return savedEntities.stream()
                .map(categoryDataAccessMapper::categoryEntityToCategory)
                .collect(Collectors.toList());
    }


    @Override
    public boolean existsByName(String name) {
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
        if (name == null || categoryIdToExclude == null || categoryIdToExclude.getValue() == null) {
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
                .map(categoryDataAccessMapper::categoryEntityToCategory);
    }

    @Override
    public List<Category> findAllById(List<CategoryId> categoryIdList) {
        if (categoryIdList == null || categoryIdList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ids = categoryIdList.stream()
                .map(CategoryId::getValue)
                .collect(Collectors.toList());

        List<CategoryEntity> foundEntities = categoryJpaRepository.findAllById(ids);

        return foundEntities.stream()
                .map(categoryDataAccessMapper::categoryEntityToCategory)
                .collect(Collectors.toList());
    }

    @Override
    public List<Category> findAllSubTreeById(CategoryId categoryId) {
        if (categoryId == null || categoryId.getValue() == null) {
            return Collections.emptyList();
        }

        List<CategoryEntity> subTreeEntities = categoryJpaRepository.findSubTreeByIdNative(categoryId.getValue());

        return subTreeEntities.stream()
                .map(categoryDataAccessMapper::categoryEntityToCategory)
                .collect(Collectors.toList());
    }

    @Override
    public List<Category> findSubTreeByIdAndStatusIn(CategoryId categoryId, List<String> statusList) {
        if (categoryId == null || categoryId.getValue() == null || statusList == null || statusList.isEmpty()) {
            return Collections.emptyList();
        }
        List<CategoryEntity> subTreeEntities = categoryJpaRepository.findSubTreeByIdAndStatusInNative(categoryId.getValue(), statusList);

        return subTreeEntities.stream()
                .map(categoryDataAccessMapper::categoryEntityToCategory)
                .collect(Collectors.toList());
    }

    @Override
    public List<Category> findAllAncestorsById(CategoryId categoryId) {
        if (categoryId == null || categoryId.getValue() == null) {
            return Collections.emptyList();
        }
        List<CategoryEntity> ancestorEntities = categoryJpaRepository.findAncestorsByIdNative(categoryId.getValue());

        return ancestorEntities.stream()
                .map(categoryDataAccessMapper::categoryEntityToCategory)
                .collect(Collectors.toList());
    }

    @Override
    public int getDepth(CategoryId categoryId) {
        if (categoryId == null || categoryId.getValue() == null) {
            throw new IllegalArgumentException("CategoryId object can not be null.");
        }
        Integer depth = categoryJpaRepository.getDepthByIdNative(categoryId.getValue());
        return (depth != null) ? depth : 0;
    }

    @Override
    public void updateStatusForIds(String status, List<CategoryId> categoryIdList) {
        if (status == null || categoryIdList == null || categoryIdList.isEmpty()) {
            return;
        }
        List<Long> longIds = categoryIdList.stream().map(CategoryId::getValue).toList();
        categoryJpaRepository.updateStatusForIds(status, longIds);
    }

    private CategoryEntity getParentRef(CategoryId parentId, Map<Long, CategoryEntity> parentRefCache) {
        if (parentId == null) return null;
        Long id = parentId.getValue();
        return parentRefCache.computeIfAbsent(id, categoryJpaRepository::getReferenceById);
    }
}
