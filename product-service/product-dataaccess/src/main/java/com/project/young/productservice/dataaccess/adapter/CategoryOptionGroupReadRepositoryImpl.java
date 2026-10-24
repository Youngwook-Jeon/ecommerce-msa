package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.CategoryOptionGroupReadRepository;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadCategoryOptionValueView;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.domain.valueobject.AttributeStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.project.young.productservice.dataaccess.entity.QAttributeDefinitionEntity.attributeDefinitionEntity;
import static com.project.young.productservice.dataaccess.entity.QCategoryAttributeEntity.categoryAttributeEntity;
import static com.project.young.productservice.dataaccess.entity.QOptionGroupEntity.optionGroupEntity;
import static com.project.young.productservice.dataaccess.entity.QOptionValueEntity.optionValueEntity;
import static com.project.young.productservice.dataaccess.entity.QAttributeValueEntity.attributeValueEntity;

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
        var groups = availableGroupsQuery(categoryId).fetch();
        log.debug("Available category option groups queried: categoryId={}, count={}", categoryId, groups.size());
        return groups;
    }

    @Override
    public List<ReadCategoryOptionGroupView> findAvailableByCategoryIdAndIds(Long categoryId, Set<UUID> groupIds) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return availableGroupsQuery(categoryId).where(optionGroupEntity.id.in(groupIds)).fetch();
    }

    @Override
    public List<ReadCategoryOptionValueView> findActiveValuesByGroupIds(Set<UUID> groupIds) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return queryFactory.select(Projections.constructor(ReadCategoryOptionValueView.class,
                        optionValueEntity.optionGroup.id,
                        optionValueEntity.id,
                        optionValueEntity.value,
                        optionValueEntity.displayName,
                        optionValueEntity.sortOrder,
                        attributeValueEntity.id,
                        attributeValueEntity.attributeId,
                        attributeValueEntity.dataType,
                        attributeValueEntity.displayName,
                        attributeValueEntity.textValue,
                        attributeValueEntity.numericValue,
                        attributeValueEntity.booleanValue,
                        attributeValueEntity.dateValue))
                .from(optionValueEntity)
                .join(attributeValueEntity).on(attributeValueEntity.id.eq(optionValueEntity.attributeValueId),
                        attributeValueEntity.attributeId.eq(optionValueEntity.attributeId))
                .where(optionValueEntity.optionGroup.id.in(groupIds), optionValueEntity.status.eq(OptionStatusEntity.ACTIVE))
                .orderBy(optionValueEntity.optionGroup.id.asc(), optionValueEntity.sortOrder.asc(), optionValueEntity.id.asc())
                .fetch();
    }

    private JPAQuery<ReadCategoryOptionGroupView> availableGroupsQuery(Long categoryId) {
        return queryFactory
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
                .orderBy(categoryAttributeEntity.displayOrder.asc(), attributeDefinitionEntity.code.asc(), optionGroupEntity.id.asc());
    }
}
