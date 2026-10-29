package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.mapper.OptionGroupAggregateMapper;
import com.project.young.productservice.dataaccess.mapper.OptionGroupDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.OptionGroupJpaRepository;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.valueobject.OptionGroupAttributeBinding;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Persistence;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.project.young.productservice.dataaccess.entity.QOptionGroupEntity.optionGroupEntity;

@Repository
@Transactional(readOnly = true)
public class OptionGroupRepositoryImpl implements OptionGroupRepository {

    private final OptionGroupJpaRepository optionGroupJpaRepository;
    private final OptionGroupDataAccessMapper optionGroupDataAccessMapper;
    private final OptionGroupAggregateMapper optionGroupAggregateMapper;
    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public OptionGroupRepositoryImpl(OptionGroupJpaRepository optionGroupJpaRepository,
                                     OptionGroupDataAccessMapper optionGroupDataAccessMapper,
                                     OptionGroupAggregateMapper optionGroupAggregateMapper,
                                     EntityManager entityManager, JPAQueryFactory queryFactory) {
        this.optionGroupJpaRepository = optionGroupJpaRepository;
        this.optionGroupDataAccessMapper = optionGroupDataAccessMapper;
        this.optionGroupAggregateMapper = optionGroupAggregateMapper;
        this.entityManager = entityManager;
        this.queryFactory = queryFactory;
    }

    @Override
    @Transactional
    public void insert(OptionGroup optionGroup) {
        if (optionGroup == null) {
            throw new IllegalArgumentException("optionGroup must not be null.");
        }
        if (optionGroup.getId() == null) {
            throw new IllegalArgumentException("optionGroup id must not be null for insert.");
        }
        OptionGroupEntity toPersist = optionGroupDataAccessMapper.domainToEntity(optionGroup);
        entityManager.persist(toPersist);
    }

    @Override
    @Transactional
    public void update(OptionGroup optionGroup) {
        if (optionGroup == null) {
            throw new IllegalArgumentException("optionGroup must not be null.");
        }
        if (optionGroup.getId() == null) {
            throw new IllegalArgumentException("optionGroup id must not be null for update.");
        }
        UUID id = optionGroup.getId().getValue();
        OptionGroupEntity current = optionGroupJpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("OptionGroup not found: " + id));
        if (entityManager.getLockMode(current) != LockModeType.PESSIMISTIC_WRITE) {
            entityManager.lock(current, LockModeType.PESSIMISTIC_WRITE);
        }
        current = loadValuesIfNeeded(current);
        mergeDomainIntoEntity(optionGroup, current);
        // Let dirty checking flush changes on commit.
    }

    @Override
    public Optional<OptionGroup> findById(OptionGroupId optionGroupId) {
        if (optionGroupId == null) {
            throw new IllegalArgumentException("optionGroupId must not be null.");
        }

        return optionGroupJpaRepository.findAggregateById(optionGroupId.getValue())
                .map(entity -> optionGroupAggregateMapper.toOptionGroup(entity, entity.getOptionValues()));
    }

    @Override
    public Optional<OptionGroupAttributeBinding> findAttributeBinding(OptionGroupId optionGroupId) {
        if (optionGroupId == null) {
            throw new IllegalArgumentException("optionGroupId must not be null.");
        }
        var row = queryFactory.select(optionGroupEntity.id, optionGroupEntity.attributeId)
                .from(optionGroupEntity).where(optionGroupEntity.id.eq(optionGroupId.getValue())).fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        UUID attributeId = row.get(optionGroupEntity.attributeId);
        return Optional.of(new OptionGroupAttributeBinding(new OptionGroupId(row.get(optionGroupEntity.id)),
                attributeId == null ? null : new AttributeDefinitionId(attributeId)));
    }

    @Override
    @Transactional
    public Optional<OptionGroup> findByIdForUpdate(OptionGroupId optionGroupId) {
        if (optionGroupId == null) {
            throw new IllegalArgumentException("optionGroupId must not be null.");
        }
        return optionGroupJpaRepository.findLockedById(optionGroupId.getValue())
                .map(this::loadValuesIfNeeded)
                .map(entity -> optionGroupAggregateMapper.toOptionGroup(entity, entity.getOptionValues()));
    }

    @Override
    public boolean existsByName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank.");
        }
        return optionGroupJpaRepository.existsByName(name);
    }

    private OptionGroupEntity loadValuesIfNeeded(OptionGroupEntity entity) {
        if (Persistence.getPersistenceUtil().isLoaded(entity, "optionValues")) {
            return entity;
        }
        return optionGroupJpaRepository.findAggregateById(entity.getId())
                .orElseThrow(() -> new IllegalStateException("Locked option group disappeared before loading values."));
    }

    private void mergeDomainIntoEntity(OptionGroup domain, OptionGroupEntity entity) {
        UUID attributeId = domain.getAttributeId() == null ? null : domain.getAttributeId().getValue();
        if (!Objects.equals(entity.getAttributeId(), attributeId)) {
            throw new IllegalArgumentException("An option group's attribute mapping cannot be changed.");
        }
        entity.setName(domain.getName());
        entity.setDisplayName(domain.getDisplayName());
        entity.setStatus(optionGroupDataAccessMapper.toEntityStatus(domain.getStatus()));

        Map<UUID, OptionValue> domainValuesMap = domain.getOptionValues().stream()
                .collect(Collectors.toMap(
                        v -> v.getId().getValue(),
                        v -> v,
                        (existing, replacement) -> {
                            throw new IllegalStateException("Data integrity error: Duplicate OptionValue ID found -> " + existing.getId().getValue());
                        }
                ));

        for (OptionValueEntity valEntity : entity.getOptionValues()) {
            OptionValue domainVal = domainValuesMap.get(valEntity.getId());

            if (domainVal != null) {
                UUID attributeValueId = domainVal.getAttributeValueId() == null ? null : domainVal.getAttributeValueId().getValue();
                if (!Objects.equals(valEntity.getAttributeValueId(), attributeValueId)) {
                    throw new IllegalArgumentException("An option value's attribute mapping cannot be changed.");
                }
                valEntity.setValue(domainVal.getValue());
                valEntity.setDisplayName(domainVal.getDisplayName());
                valEntity.setSortOrder(domainVal.getSortOrder());
                valEntity.setStatus(optionGroupDataAccessMapper.toEntityStatus(domainVal.getStatus()));

                domainValuesMap.remove(valEntity.getId());
            }
        }

        for (OptionValue newDomainVal : domainValuesMap.values()) {
            OptionValueEntity newValEntity = optionGroupDataAccessMapper.optionValueDomainToEntity(newDomainVal);
            entity.addOptionValue(newValEntity);
        }
    }
}
