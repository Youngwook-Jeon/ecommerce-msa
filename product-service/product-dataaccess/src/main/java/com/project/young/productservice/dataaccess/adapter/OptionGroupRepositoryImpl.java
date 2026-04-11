package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.mapper.OptionGroupAggregateMapper;
import com.project.young.productservice.dataaccess.mapper.OptionGroupDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.OptionGroupJpaRepository;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class OptionGroupRepositoryImpl implements OptionGroupRepository {

    private final OptionGroupJpaRepository optionGroupJpaRepository;
    private final OptionGroupDataAccessMapper optionGroupDataAccessMapper;
    private final OptionGroupAggregateMapper optionGroupAggregateMapper;
    private final EntityManager entityManager;

    public OptionGroupRepositoryImpl(OptionGroupJpaRepository optionGroupJpaRepository,
                                     OptionGroupDataAccessMapper optionGroupDataAccessMapper,
                                     OptionGroupAggregateMapper optionGroupAggregateMapper,
                                     EntityManager entityManager) {
        this.optionGroupJpaRepository = optionGroupJpaRepository;
        this.optionGroupDataAccessMapper = optionGroupDataAccessMapper;
        this.optionGroupAggregateMapper = optionGroupAggregateMapper;
        this.entityManager = entityManager;
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
        mergeDomainIntoEntity(optionGroup, current);
        // Let dirty checking flush changes on commit.
    }

    @Override
    public Optional<OptionGroup> findById(OptionGroupId optionGroupId) {
        if (optionGroupId == null) {
            throw new IllegalArgumentException("optionGroupId must not be null.");
        }

        return optionGroupJpaRepository.findAggregateById(optionGroupId.getValue())
                .map(optionGroupAggregateMapper::toOptionGroup);
    }

    @Override
    public boolean existsByName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank.");
        }
        return optionGroupJpaRepository.existsByName(name);
    }

    private void mergeDomainIntoEntity(OptionGroup domain, OptionGroupEntity entity) {
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
