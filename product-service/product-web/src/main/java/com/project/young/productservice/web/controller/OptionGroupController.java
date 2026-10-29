package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.command.AddOptionValuesCommand;
import com.project.young.productservice.application.dto.command.CreateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.UpdateOptionGroupCommand;
import com.project.young.productservice.application.dto.command.UpdateOptionValueCommand;
import com.project.young.productservice.application.service.OptionGroupApplicationService;
import com.project.young.productservice.web.dto.*;
import com.project.young.productservice.web.mapper.OptionGroupResponseMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
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

    /**
     * Creates a dictionary group bound to one active attribute definition. The binding is immutable.
     * A definition can have only one dictionary group, including inactive or deleted groups.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<CreateOptionGroupResponse> createGroup(@Valid @RequestBody CreateOptionGroupCommand command) {
        log.info("A post request to create Option Group: {}", command.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(optionGroupResponseMapper.toCreateOptionGroupResponse(
                        optionGroupApplicationService.createOptionGroup(command)));
    }

    /**
     * Edits group information without loading or modifying option values.
     * Requires expectedVersion from the admin query response. Stale or concurrent edits return
     * 409 OPTION_GROUP_VERSION_CONFLICT. Reload the query after success to obtain the next version.
     * Information edits use optimistic locking; the delete endpoint retains group locking for cascading deletion.
     * Reactivation requires a linked, active attribute definition and locks that definition before updating the group.
     * Other information edits and deactivation do not require an active attribute definition.
     */
    @PutMapping("/{groupId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UpdateOptionGroupResponse> updateGroup(
            @PathVariable("groupId") UUID groupId,
            @Valid @RequestBody UpdateOptionGroupCommand command,
            @RequestParam("expectedVersion") @PositiveOrZero long expectedVersion) {
        log.info("A put request to update Option Group with id: {}", groupId);
        return ResponseEntity.ok(optionGroupResponseMapper.toUpdateOptionGroupResponse(
                optionGroupApplicationService.updateOptionGroup(groupId, command, expectedVersion)));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<DeleteOptionGroupResponse> deleteGroup(@PathVariable("groupId") UUID groupId) {
        log.info("A delete request to soft-delete Option Group with id: {}", groupId);
        return ResponseEntity.ok(optionGroupResponseMapper.toDeleteOptionGroupResponse(
                optionGroupApplicationService.deleteOptionGroup(groupId)));
    }

    /**
     * Adds choices referencing canonical values of the group's active attribute definition.
     * Bindings cannot be reassigned through update endpoints; labels and option codes remain editable.
     * Legacy unlinked groups cannot accept new values until explicitly migrated.
     */
    @PostMapping("/{groupId}/option-values")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<AddOptionValuesResponse> addValues(
            @PathVariable("groupId") UUID groupId,
            @Valid @RequestBody AddOptionValuesCommand command) {
        log.info("A post request to add multiple Option Values to Group id: {}", groupId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(optionGroupResponseMapper.toAddOptionValuesResponse(
                        optionGroupApplicationService.addOptionValues(groupId, command)));
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
