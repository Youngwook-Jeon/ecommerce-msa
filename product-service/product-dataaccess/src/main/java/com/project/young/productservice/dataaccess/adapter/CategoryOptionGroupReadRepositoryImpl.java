package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.CategoryOptionGroupReadRepository;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.domain.valueobject.AttributeStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.project.young.productservice.dataaccess.entity.QAttributeDefinitionEntity.attributeDefinitionEntity;
import static com.project.young.productservice.dataaccess.entity.QCategoryAttributeEntity.categoryAttributeEntity;
import static com.project.young.productservice.dataaccess.entity.QOptionGroupEntity.optionGroupEntity;

@Repository
@Transactional(readOnly = true)
@Slf4j
public class CategoryOptionGroupReadRepositoryImpl implements CategoryOptionGroupReadRepository {
    private final JPAQueryFactory queryFactory;

    public CategoryOptionGroupReadRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<ReadCategoryOptionGroupView> findAvailableByCategoryId(Long categoryId) {
        List<ReadCategoryOptionGroupView> groups = queryFactory
                .select(Projections.constructor(ReadCategoryOptionGroupView.class,
                        optionGroupEntity.id,
                        optionGroupEntity.name,
                        optionGroupEntity.displayName,
                        attributeDefinitionEntity.id,
                        attributeDefinitionEntity.code,
                        attributeDefinitionEntity.displayName,
                        attributeDefinitionEntity.dataType,
                        attributeDefinitionEntity.canonicalUnit,
                        categoryAttributeEntity.required,
                        categoryAttributeEntity.displayOrder))
                .from(categoryAttributeEntity)
                .join(attributeDefinitionEntity).on(attributeDefinitionEntity.id.eq(categoryAttributeEntity.attributeId))
                .join(optionGroupEntity).on(optionGroupEntity.attributeId.eq(attributeDefinitionEntity.id))
                .where(categoryAttributeEntity.categoryId.eq(categoryId),
                        categoryAttributeEntity.enabled.isTrue(),
                        categoryAttributeEntity.optionAllowed.isTrue(),
                        attributeDefinitionEntity.status.eq(AttributeStatus.ACTIVE),
                        optionGroupEntity.status.eq(OptionStatusEntity.ACTIVE))
                .orderBy(categoryAttributeEntity.displayOrder.asc(), attributeDefinitionEntity.code.asc(), optionGroupEntity.id.asc())
                .fetch();
        log.debug("Available category option groups queried: categoryId={}, count={}", categoryId, groups.size());
        return groups;
    }
}
