package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.application.dto.command.AddOptionValueCommand;
import com.project.young.productservice.application.dto.command.CreateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.UpdateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.UpdateOptionValueCommand;
import com.project.young.productservice.application.dto.result.*;
import com.project.young.productservice.application.mapper.OptionGroupDataMapper;
import com.project.young.productservice.application.port.output.IdGenerator;
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

import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class OptionGroupApplicationService {

    private final OptionGroupRepository optionGroupRepository;
    private final OptionGroupDomainService optionGroupDomainService;
    private final OptionGroupDataMapper optionGroupDataMapper;
    private final IdGenerator idGenerator;

    public OptionGroupApplicationService(OptionGroupRepository optionGroupRepository,
                                         OptionGroupDomainService optionGroupDomainService,
                                         OptionGroupDataMapper optionGroupDataMapper,
                                         IdGenerator idGenerator) {
        this.optionGroupRepository = optionGroupRepository;
        this.optionGroupDomainService = optionGroupDomainService;
        this.optionGroupDataMapper = optionGroupDataMapper;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public CreateOptionGroupResult createOptionGroup(CreateOptionGroupCommand command) {
        validateCreateRequest(command);
        log.info("Attempting to create option group with name: {}", command.getName());

        if (!optionGroupDomainService.isValidOptionGroupName(command.getName())) {
            log.warn("Option group name already exists: {}", command.getName());
            throw new DuplicateOptionGroupNameException("Option group name '" + command.getName() + "' already exists.");
        }

        OptionGroupId newGroupId = new OptionGroupId(idGenerator.generateId());
        OptionGroup newGroup = optionGroupDataMapper.toOptionGroup(command, newGroupId);
        OptionGroup saved = optionGroupRepository.insert(newGroup);

        log.info("Option group saved successfully with id: {}", saved.getId().getValue());

        return optionGroupDataMapper.toCreateOptionGroupResult(saved);
    }

    @Transactional
    public AddOptionValueResult addOptionValue(UUID optionGroupIdValue, AddOptionValueCommand command) {
        validateAddRequest(optionGroupIdValue, command);
        OptionGroupId groupId = new OptionGroupId(optionGroupIdValue);

        log.info("Attempting to add option value to group: {}", groupId.getValue());
        OptionGroup optionGroup = optionGroupRepository.findById(groupId)
                .orElseThrow(() -> new OptionGroupNotFoundException("Option group not found."));

        validateOptionGroupCanBeModified(optionGroup);

        OptionValueId newValueId = new OptionValueId(idGenerator.generateId());
        OptionValue newValue = optionGroupDataMapper.toOptionValue(command, newValueId);

        optionGroup.addOptionValue(newValue);
        optionGroupRepository.update(optionGroup);

        log.info("Option value saved successfully with id: {} to group: {}", newValueId.getValue(), groupId.getValue());

        return optionGroupDataMapper.toAddOptionValueResult(newValue);
    }

    @Transactional
    public UpdateOptionGroupResult updateOptionGroup(UUID optionGroupIdValue, UpdateOptionGroupCommand command) {
        validateUpdateRequest(optionGroupIdValue, command);
        OptionGroupId groupId = new OptionGroupId(optionGroupIdValue);

        log.info("Attempting to update option group with id: {}", groupId.getValue());

        OptionGroup optionGroup = optionGroupRepository.findById(groupId)
                .orElseThrow(() -> new OptionGroupNotFoundException("Option group not found."));

        validateOptionGroupCanBeModified(optionGroup);

        boolean isModified = false;
        isModified |= applyNameChange(optionGroup, command.getName());
        isModified |= applyDisplayNameChange(optionGroup, command.getDisplayName());
        isModified |= applyStatusChange(optionGroup, command.getStatus());

        if (isModified) {
            optionGroupRepository.update(optionGroup);
            log.info("Option group updated successfully. id: {}", optionGroup.getId().getValue());
        }

        return optionGroupDataMapper.toUpdateOptionGroupResult(optionGroup);
    }

    @Transactional
    public UpdateOptionValueResult updateOptionValue(UUID optionGroupIdValue, UUID optionValueIdValue, UpdateOptionValueCommand command) {
        if (optionGroupIdValue == null || optionValueIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid option value update request.");
        }

        OptionGroupId groupId = new OptionGroupId(optionGroupIdValue);
        OptionValueId valueId = new OptionValueId(optionValueIdValue);

        log.info("Attempting to update option value with id: {} in group: {}", valueId.getValue(), groupId.getValue());

        OptionGroup optionGroup = optionGroupRepository.findById(groupId)
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

        OptionGroup optionGroup = optionGroupRepository.findById(groupId)
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

        OptionGroup optionGroup = optionGroupRepository.findById(groupId)
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

    private void validateAddRequest(UUID optionGroupIdValue, AddOptionValueCommand command) {
        if (optionGroupIdValue == null || command == null) {
            throw new IllegalArgumentException("Add option value command cannot be null");
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

    private boolean applyNameChange(OptionGroup optionGroup, String newName) {
        if (newName != null && !Objects.equals(optionGroup.getName(), newName)) {
            if (!optionGroupDomainService.isValidOptionGroupName(newName)) {
                throw new DuplicateOptionGroupNameException("Option group name '" + newName + "' already exists.");
            }
            optionGroup.changeName(newName);
            return true;
        }
        return false;
    }

    private boolean applyDisplayNameChange(OptionGroup optionGroup, String newDisplayName) {
        if (newDisplayName != null && !Objects.equals(optionGroup.getDisplayName(), newDisplayName)) {
            optionGroup.changeDisplayName(newDisplayName);
            return true;
        }
        return false;
    }

    private boolean applyStatusChange(OptionGroup optionGroup, OptionStatus newStatus) {
        if (newStatus != null && optionGroup.getStatus() != newStatus) {
            optionGroup.changeStatus(newStatus);
            return true;
        }
        return false;
    }
}