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

import java.util.Optional;

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
        CategoryEntity parentEntity = null;
        if (category.getParentId().isPresent()) {
            parentEntity = categoryJpaRepository.findById(category.getParentId().get().getValue())
                    .orElseThrow(() -> new EntityNotFoundException("The parent category does not exist"));
        }

        CategoryEntity categoryEntity = categoryDataAccessMapper.categoryToCategoryEntity(category, parentEntity);
        if (parentEntity != null) {
            parentEntity.addChild(categoryEntity);
        }

        return categoryDataAccessMapper.categoryEntityToCategory(categoryJpaRepository.save(categoryEntity));
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
}
