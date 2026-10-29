package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.application.dto.command.AddOptionValueCommand;
import com.project.young.productservice.application.dto.command.AddOptionValuesCommand;
import com.project.young.productservice.application.dto.command.CreateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.UpdateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.UpdateOptionValueCommand;
import com.project.young.productservice.application.dto.result.*;
import com.project.young.productservice.application.exception.OptionGroupVersionConflictException;
import com.project.young.productservice.application.mapper.OptionGroupDataMapper;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.port.output.OptionGroupDetailsPort;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.exception.DuplicateOptionGroupNameException;
import com.project.young.productservice.domain.exception.OptionDomainException;
import com.project.young.productservice.domain.exception.OptionGroupNotFoundException;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.service.OptionGroupDomainService;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class OptionGroupApplicationService {

    private final OptionGroupRepository optionGroupRepository;
    private final OptionGroupDomainService optionGroupDomainService;
    private final OptionGroupDataMapper optionGroupDataMapper;
    private final IdGenerator idGenerator;
    private final OptionAttributeValidator optionAttributeValidator;
    private final OptionGroupDetailsPort optionGroupDetailsPort;

    public OptionGroupApplicationService(OptionGroupRepository optionGroupRepository,
                                         OptionGroupDomainService optionGroupDomainService,
                                         OptionGroupDataMapper optionGroupDataMapper,
                                         IdGenerator idGenerator,
                                         OptionAttributeValidator optionAttributeValidator,
                                         OptionGroupDetailsPort optionGroupDetailsPort) {
        this.optionGroupRepository = optionGroupRepository;
        this.optionGroupDomainService = optionGroupDomainService;
        this.optionGroupDataMapper = optionGroupDataMapper;
        this.idGenerator = idGenerator;
        this.optionAttributeValidator = optionAttributeValidator;
        this.optionGroupDetailsPort = optionGroupDetailsPort;
    }

    @Transactional
    public CreateOptionGroupResult createOptionGroup(CreateOptionGroupCommand command) {
        validateCreateRequest(command);
        log.info("Attempting to create option group with name: {}", command.getName());

        if (!optionGroupDomainService.isValidOptionGroupName(command.getName())) {
            log.warn("Option group name already exists: {}", command.getName());
            throw new DuplicateOptionGroupNameException("Option group name '" + command.getName() + "' already exists.");
        }

        optionAttributeValidator.validateDefinition(command.getAttributeId());

        OptionGroupId newGroupId = new OptionGroupId(idGenerator.generateId());
        OptionGroup newGroup = optionGroupDataMapper.toOptionGroup(command, newGroupId);
        optionGroupRepository.insert(newGroup);

        log.info("Option group saved successfully with id: {}", newGroup.getId().getValue());

        return optionGroupDataMapper.toCreateOptionGroupResult(newGroup);
    }

    @Transactional
    public List<AddOptionValueResult> addOptionValues(UUID optionGroupIdValue, AddOptionValuesCommand command) {
        validateAddValuesRequest(optionGroupIdValue, command);
        OptionGroupId groupId = new OptionGroupId(optionGroupIdValue);

        log.info("Attempting to add option values to group: {}", groupId.getValue());
        OptionGroup optionGroup = optionAttributeValidator.lockGroupAndValidateValues(groupId, command.getOptionValues());
        validateOptionGroupCanBeModified(optionGroup);

        List<AddOptionValueResult> results = new ArrayList<>();

        for (AddOptionValueCommand valueCommand : command.getOptionValues()) {
            if (valueCommand == null) {
                throw new IllegalArgumentException("Option value command must not be null.");
            }

            OptionValueId newValueId = new OptionValueId(idGenerator.generateId());
            OptionValue newValue = optionGroupDataMapper.toOptionValue(valueCommand, newValueId);

            optionGroup.addOptionValue(newValue);
            results.add(optionGroupDataMapper.toAddOptionValueResult(newValue));
        }

        optionGroupRepository.update(optionGroup);

        return results;
    }

    @Transactional
    public UpdateOptionGroupResult updateOptionGroup(UUID optionGroupIdValue, UpdateOptionGroupCommand command, long expectedVersion) {
        validateUpdateRequest(optionGroupIdValue, command);
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Expected option group version must not be negative");
        }
        OptionGroupId groupId = new OptionGroupId(optionGroupIdValue);

        log.info("Attempting to update option group with id: {}", groupId.getValue());

        var original = optionGroupDetailsPort.find(groupId)
                .orElseThrow(() -> new OptionGroupNotFoundException("Option group not found."));
        if (original.version() != expectedVersion) {
            throw new OptionGroupVersionConflictException(optionGroupIdValue, expectedVersion);
        }
        original.requireMutable();
        if (original.status() == OptionStatus.INACTIVE && command.getStatus() == OptionStatus.ACTIVE) {
            optionAttributeValidator.validateActivation(groupId);
        }
        var updated = original;
        if (command.getName() != null && !Objects.equals(updated.name(), command.getName())) {
            if (!optionGroupDomainService.isValidOptionGroupName(command.getName())) {
                throw new DuplicateOptionGroupNameException("Option group name '" + command.getName() + "' already exists.");
            }
            updated = updated.rename(command.getName());
        }
        if (command.getDisplayName() != null && !Objects.equals(updated.displayName(), command.getDisplayName())) {
            updated = updated.changeDisplayName(command.getDisplayName());
        }
        if (command.getStatus() != null && updated.status() != command.getStatus()) {
            updated = updated.changeStatus(command.getStatus());
        }
        if (!updated.equals(original)) {
            optionGroupDetailsPort.update(updated);
            log.info("Option group updated successfully. id: {}", updated.id().getValue());
        }
        return optionGroupDataMapper.toUpdateOptionGroupResult(updated);
    }

    @Transactional
    public UpdateOptionValueResult updateOptionValue(UUID optionGroupIdValue, UUID optionValueIdValue, UpdateOptionValueCommand command) {
        if (optionGroupIdValue == null || optionValueIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid option value update request.");
        }

        OptionGroupId groupId = new OptionGroupId(optionGroupIdValue);
        OptionValueId valueId = new OptionValueId(optionValueIdValue);

        log.info("Attempting to update option value with id: {} in group: {}", valueId.getValue(), groupId.getValue());

        OptionGroup optionGroup = optionGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new OptionGroupNotFoundException("Option group not found."));

        validateOptionGroupCanBeModified(optionGroup);
        optionGroup.updateOptionValueDetails(valueId, command.getValue(), command.getDisplayName(), command.getSortOrder(), command.getStatus());
        optionGroupRepository.update(optionGroup);

        log.info("Option value updated successfully. id: {}", valueId.getValue());

        OptionValue updatedOptionValue = optionGroup.getOptionValue(valueId);

        return optionGroupDataMapper.toUpdateOptionValueResult(updatedOptionValue);
    }

    @Transactional
    public DeleteOptionGroupResult deleteOptionGroup(UUID optionGroupIdValue) {
        if (optionGroupIdValue == null) {
            throw new IllegalArgumentException("Option group ID for delete cannot be null.");
        }
        OptionGroupId groupId = new OptionGroupId(optionGroupIdValue);
        log.info("Attempting to soft-delete option group with id: {}", groupId.getValue());

        OptionGroup optionGroup = optionGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new OptionGroupNotFoundException("Option group not found."));

        // 도메인 로직: 애그리거트 루트와 하위 옵션 값들을 연쇄적으로 소프트 삭제
        optionGroup.markAsDeleted();
        optionGroupRepository.update(optionGroup);

        log.info("Option group soft-deleted successfully. id: {}", optionGroup.getId().getValue());

        return optionGroupDataMapper.toDeleteOptionGroupResult(optionGroup);
    }

    @Transactional
    public DeleteOptionValueResult deleteOptionValue(UUID optionGroupIdValue, UUID optionValueIdValue) {
        if (optionGroupIdValue == null || optionValueIdValue == null) {
            throw new IllegalArgumentException("IDs for deleting an option value cannot be null.");
        }
        OptionGroupId groupId = new OptionGroupId(optionGroupIdValue);
        OptionValueId valueId = new OptionValueId(optionValueIdValue);

        log.info("Attempting to soft-delete option value with id: {} from group: {}", valueId.getValue(), groupId.getValue());

        OptionGroup optionGroup = optionGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new OptionGroupNotFoundException("Option group not found."));

        validateOptionGroupCanBeModified(optionGroup);

        // 도메인 로직: 특정 옵션 값 하나만 비활성화 (소프트 삭제)
        optionGroup.deleteOptionValue(valueId);
        optionGroupRepository.update(optionGroup);

        log.info("Option value soft-deleted successfully. id: {}", valueId.getValue());

        OptionValue deletedOptionValue = optionGroup.getOptionValue(valueId);

        return optionGroupDataMapper.toDeleteOptionValueResult(deletedOptionValue);
    }

    private void validateCreateRequest(CreateOptionGroupCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Create option group command cannot be null");
        }
    }

    private void validateAddValuesRequest(UUID optionGroupIdValue, AddOptionValuesCommand command) {
        if (optionGroupIdValue == null || command == null) {
            throw new IllegalArgumentException("Add option values command cannot be null");
        }
        if (command.getOptionValues() == null || command.getOptionValues().isEmpty()) {
            throw new IllegalArgumentException("At least one option value is required");
        }
    }

    private void validateUpdateRequest(UUID optionGroupIdValue, UpdateOptionGroupCommand command) {
        if (optionGroupIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid option group update request.");
        }
    }

    private void validateOptionGroupCanBeModified(OptionGroup optionGroup) {
        if (optionGroup.isDeleted()) {
            throw new OptionDomainException("Cannot modify an option group that has been deleted.");
        }
    }

}
