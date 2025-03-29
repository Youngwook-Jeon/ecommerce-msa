package com.project.young.productservice.dataaccess.product.adapter;

import com.project.young.productservice.dataaccess.product.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.product.mapper.CategoryDataAccessMapper;
import com.project.young.productservice.dataaccess.product.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.repository.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
}
