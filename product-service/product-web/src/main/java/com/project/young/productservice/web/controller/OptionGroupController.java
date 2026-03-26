package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.command.AddOptionValueCommand;
import com.project.young.productservice.application.dto.command.CreateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.UpdateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.UpdateOptionValueCommand;
import com.project.young.productservice.application.service.OptionGroupApplicationService;
import com.project.young.productservice.web.dto.*;
import com.project.young.productservice.web.mapper.OptionGroupResponseMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("option-groups")
public class OptionGroupController {

    private final OptionGroupApplicationService optionGroupApplicationService;
    private final OptionGroupResponseMapper optionGroupResponseMapper;

    public OptionGroupController(OptionGroupApplicationService optionGroupApplicationService,
                                 OptionGroupResponseMapper optionGroupResponseMapper) {
        this.optionGroupApplicationService = optionGroupApplicationService;
        this.optionGroupResponseMapper = optionGroupResponseMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<CreateOptionGroupResponse> createGroup(@Valid @RequestBody CreateOptionGroupCommand command) {
        log.info("A post request to create Option Group: {}", command.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(optionGroupResponseMapper.toCreateOptionGroupResponse(
                        optionGroupApplicationService.createOptionGroup(command)));
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UpdateOptionGroupResponse> updateGroup(
            @PathVariable("groupId") UUID groupId,
            @Valid @RequestBody UpdateOptionGroupCommand command) {
        log.info("A put request to update Option Group with id: {}", groupId);
        return ResponseEntity.ok(optionGroupResponseMapper.toUpdateOptionGroupResponse(
                optionGroupApplicationService.updateOptionGroup(groupId, command)));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<DeleteOptionGroupResponse> deleteGroup(@PathVariable("groupId") UUID groupId) {
        log.info("A delete request to soft-delete Option Group with id: {}", groupId);
        return ResponseEntity.ok(optionGroupResponseMapper.toDeleteOptionGroupResponse(
                optionGroupApplicationService.deleteOptionGroup(groupId)));
    }

    @PostMapping("/{groupId}/option-values")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<AddOptionValueResponse> addValue(
            @PathVariable("groupId") UUID groupId,
            @Valid @RequestBody AddOptionValueCommand command) {
        log.info("A post request to add Option Value to Group id: {}", groupId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(optionGroupResponseMapper.toAddOptionValueResponse(
                        optionGroupApplicationService.addOptionValue(groupId, command)));
    }

    @PutMapping("/{groupId}/option-values/{valueId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UpdateOptionValueResponse> updateValue(
            @PathVariable("groupId") UUID groupId,
            @PathVariable("valueId") UUID valueId,
            @Valid @RequestBody UpdateOptionValueCommand command) {
        log.info("A put request to update Option Value id: {} in Group id: {}", valueId, groupId);
        return ResponseEntity.ok(optionGroupResponseMapper.toUpdateOptionValueResponse(
                optionGroupApplicationService.updateOptionValue(groupId, valueId, command)));
    }

    @DeleteMapping("/{groupId}/option-values/{valueId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<DeleteOptionValueResponse> deleteValue(
            @PathVariable("groupId") UUID groupId,
            @PathVariable("valueId") UUID valueId) {
        log.info("A delete request to soft-delete Option Value id: {} in Group id: {}", valueId, groupId);
        return ResponseEntity.ok(optionGroupResponseMapper.toDeleteOptionValueResponse(
                optionGroupApplicationService.deleteOptionValue(groupId, valueId)));
    }
}