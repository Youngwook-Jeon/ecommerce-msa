package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.view.ReadCategoryView;
import com.project.young.productservice.application.port.output.CategoryReadRepository;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.mapper.CategoryDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
@Slf4j
public class CategoryReadRepositoryImpl implements CategoryReadRepository {

    private final CategoryJpaRepository categoryJpaRepository;
    private final CategoryDataAccessMapper categoryDataAccessMapper;

    public CategoryReadRepositoryImpl(CategoryJpaRepository categoryJpaRepository,
                                      CategoryDataAccessMapper categoryDataAccessMapper) {
        this.categoryJpaRepository = categoryJpaRepository;
        this.categoryDataAccessMapper = categoryDataAccessMapper;
    }

    @Override
    public List<ReadCategoryView> findAllActiveCategoryHierarchy() {
        List<CategoryEntity> allActiveCategories = categoryJpaRepository.findAllWithParentByStatus(CategoryStatusEntity.ACTIVE);
        log.info("Found {} active categories from the database.", allActiveCategories.size());
        return buildHierarchyFromEntities(allActiveCategories);
    }

    @Override
    public List<ReadCategoryView> findAllCategoryHierarchy() {
        List<CategoryEntity> allCategories = categoryJpaRepository.findAllWithParent();
        log.info("Found {} total categories from the database for admin.", allCategories.size());
        return buildHierarchyFromEntities(allCategories);
    }

    private List<ReadCategoryView> buildHierarchyFromEntities(List<CategoryEntity> entities) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<CategoryEntity>> childrenByParentIdMap = entities.stream()
                .filter(category -> category.getParent() != null)
                .collect(Collectors.groupingBy(category -> category.getParent().getId()));

        return entities.stream()
                .filter(category -> category.getParent() == null)
                .map(rootEntity -> buildViewTree(rootEntity, childrenByParentIdMap))
                .collect(Collectors.toList());
    }

    private ReadCategoryView buildViewTree(CategoryEntity entity, Map<Long, List<CategoryEntity>> childrenMap) {
        List<CategoryEntity> childEntities = childrenMap.getOrDefault(entity.getId(), Collections.emptyList());

        // Recursively build views for each child. This creates the list of child views.
        List<ReadCategoryView> childViews = childEntities.stream()
                .map(childEntity -> buildViewTree(childEntity, childrenMap))
                .collect(Collectors.toList());

        Long parentId = (entity.getParent() != null) ? entity.getParent().getId() : null;

        return new ReadCategoryView(
                entity.getId(),
                entity.getName(),
                parentId,
                categoryDataAccessMapper.toDomainStatus(entity.getStatus()),
                childViews
        );
    }
}
