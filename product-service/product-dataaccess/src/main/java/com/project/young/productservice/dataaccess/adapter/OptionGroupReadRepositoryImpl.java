package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.port.output.OptionGroupReadRepository;
import com.project.young.productservice.application.port.output.view.ReadOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadOptionValueView;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.mapper.OptionGroupDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.OptionGroupJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
@Slf4j
public class OptionGroupReadRepositoryImpl implements OptionGroupReadRepository {

    private final OptionGroupJpaRepository optionGroupJpaRepository;
    private final OptionGroupDataAccessMapper optionGroupDataAccessMapper;

    public OptionGroupReadRepositoryImpl(OptionGroupJpaRepository optionGroupJpaRepository,
                                         OptionGroupDataAccessMapper optionGroupDataAccessMapper) {
        this.optionGroupJpaRepository = optionGroupJpaRepository;
        this.optionGroupDataAccessMapper = optionGroupDataAccessMapper;
    }

    @Override
    public List<ReadOptionGroupView> findAllActiveCatalogOptionGroups() {
        List<OptionGroupEntity> entities = optionGroupJpaRepository.findAllByStatusOrderByNameAsc(OptionStatusEntity.ACTIVE);
        log.info("Found {} active option groups for catalog.", entities.size());
        return entities.stream()
                .map(this::toCatalogOptionGroupView)
                .toList();
    }

    @Override
    public Optional<ReadOptionGroupView> findActiveCatalogOptionGroupById(OptionGroupId optionGroupId) {
        if (optionGroupId == null) {
            throw new IllegalArgumentException("optionGroupId must not be null.");
        }

        return optionGroupJpaRepository.findByIdAndStatus(optionGroupId.getValue(), OptionStatusEntity.ACTIVE)
                .map(this::toCatalogOptionGroupView);
    }

    @Override
    public List<ReadOptionGroupView> findAllAdminOptionGroups() {
        List<OptionGroupEntity> entities = optionGroupJpaRepository.findAllByOrderByNameAsc();
        return entities.stream()
                .map(this::toAdminOptionGroupView)
                .toList();
    }

    @Override
    public Optional<ReadOptionGroupView> findAdminOptionGroupById(OptionGroupId optionGroupId) {
        return optionGroupJpaRepository.findAggregateById(optionGroupId.getValue())
                .map(this::toAdminOptionGroupView);
    }

    private ReadOptionValueView toReadOptionValueView(OptionValueEntity entity) {
        Objects.requireNonNull(entity, "optionValue entity must not be null.");
        return ReadOptionValueView.builder()
                .id(entity.getId())
                .value(entity.getValue())
                .displayName(entity.getDisplayName())
                .sortOrder(entity.getSortOrder())
                .status(optionGroupDataAccessMapper.toDomainStatus(entity.getStatus()))
                .build();
    }

    private ReadOptionGroupView toCatalogOptionGroupView(OptionGroupEntity entity) {
        List<ReadOptionValueView> activeValues = entity.getOptionValues().stream()
                .filter(v -> v.getStatus() == OptionStatusEntity.ACTIVE)
                .sorted(Comparator.comparingInt(OptionValueEntity::getSortOrder))
                .map(this::toReadOptionValueView)
                .toList();

        return buildView(entity, activeValues);
    }

    private ReadOptionGroupView toAdminOptionGroupView(OptionGroupEntity entity) {
        List<ReadOptionValueView> allValues = entity.getOptionValues().stream()
                .sorted(Comparator.comparingInt(OptionValueEntity::getSortOrder))
                .map(this::toReadOptionValueView)
                .toList();

        return buildView(entity, allValues);
    }

    private ReadOptionGroupView buildView(OptionGroupEntity entity, List<ReadOptionValueView> optionValues) {
        return ReadOptionGroupView.builder()
                .id(entity.getId())
                .name(entity.getName())
                .displayName(entity.getDisplayName())
                .status(optionGroupDataAccessMapper.toDomainStatus(entity.getStatus()))
                .optionValues(optionValues)
                .build();
    }
}
