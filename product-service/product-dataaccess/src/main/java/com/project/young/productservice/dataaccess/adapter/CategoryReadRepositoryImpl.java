package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.CategoryDto;
import com.project.young.productservice.application.port.output.CategoryReadRepository;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.entity.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class CategoryReadRepositoryImpl implements CategoryReadRepository {

    private final CategoryJpaRepository categoryJpaRepository;

    public CategoryReadRepositoryImpl(CategoryJpaRepository categoryJpaRepository) {
        this.categoryJpaRepository = categoryJpaRepository;
    }

    @Override
    public List<CategoryDto> findAllActiveCategoryHierarchy() {
        List<CategoryEntity> allActiveCategories = categoryJpaRepository.findAllWithParentByStatus(Category.STATUS_ACTIVE);
        log.info("Found {} active categories from the database.", allActiveCategories.size());
        return buildHierarchyFromEntities(allActiveCategories);
    }

    @Override
    public List<CategoryDto> findAllCategoryHierarchy() {
        List<CategoryEntity> allCategories = categoryJpaRepository.findAllWithParent();
        log.info("Found {} total categories from the database for admin.", allCategories.size());
        return buildHierarchyFromEntities(allCategories);
    }

    private List<CategoryDto> buildHierarchyFromEntities(List<CategoryEntity> entities) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<CategoryEntity>> childrenByParentIdMap = entities.stream()
                .filter(category -> category.getParent() != null)
                .collect(Collectors.groupingBy(category -> category.getParent().getId()));

        return entities.stream()
                .filter(category -> category.getParent() == null)
                .map(rootEntity -> buildDtoTree(rootEntity, childrenByParentIdMap))
                .collect(Collectors.toList());
    }

    private CategoryDto buildDtoTree(CategoryEntity entity, Map<Long, List<CategoryEntity>> childrenMap) {
        List<CategoryEntity> childEntities = childrenMap.getOrDefault(entity.getId(), Collections.emptyList());

        // Recursively build DTOs for each child. This creates the list of child DTOs.
        List<CategoryDto> childDtos = childEntities.stream()
                .map(childEntity -> buildDtoTree(childEntity, childrenMap))
                .collect(Collectors.toList());

        Long parentId = (entity.getParent() != null) ? entity.getParent().getId() : null;

        return new CategoryDto(
                entity.getId(),
                entity.getName(),
                parentId,
                entity.getStatus(),
                childDtos
        );
    }
}
