package com.project.young.productservice.web.controller;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.port.output.view.ReadOptionGroupView;
import com.project.young.productservice.application.service.OptionGroupQueryService;
import com.project.young.productservice.web.dto.ReadOptionGroupListQueryResponse;
import com.project.young.productservice.web.dto.ReadOptionGroupQueryResponse;
import com.project.young.productservice.web.mapper.OptionGroupQueryResponseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("admin/queries/option-groups")
@Slf4j
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminOptionGroupQueryController {

    private final OptionGroupQueryService optionGroupQueryService;
    private final OptionGroupQueryResponseMapper optionGroupQueryResponseMapper;

    public AdminOptionGroupQueryController(OptionGroupQueryService optionGroupQueryService,
                                           OptionGroupQueryResponseMapper optionGroupQueryResponseMapper) {
        this.optionGroupQueryService = optionGroupQueryService;
        this.optionGroupQueryResponseMapper = optionGroupQueryResponseMapper;
    }

    @GetMapping
    public ResponseEntity<ReadOptionGroupListQueryResponse> getAllOptionGroups() {
        log.info("REST request (admin) to get all option groups");
        List<ReadOptionGroupView> views = optionGroupQueryService.getAllOptionGroupsForAdmin();
        return ResponseEntity.ok(optionGroupQueryResponseMapper.toReadOptionGroupListQueryResponse(views));
    }

    @GetMapping("/{optionGroupId}")
    public ResponseEntity<ReadOptionGroupQueryResponse> getOptionGroupDetail(
            @PathVariable("optionGroupId") UUID optionGroupId) {
        log.info("REST request (admin) to get option group detail for optionGroupId={}", optionGroupId);
        ReadOptionGroupView view =
                optionGroupQueryService.getOptionGroupDetailForAdmin(new OptionGroupId(optionGroupId));
        return ResponseEntity.ok(optionGroupQueryResponseMapper.toReadOptionGroupQueryResponse(view));
    }
}
