package com.project.young.productservice.web.controller;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.port.output.view.ReadOptionGroupView;
import com.project.young.productservice.application.service.OptionGroupQueryService;
import com.project.young.productservice.web.dto.ReadOptionGroupListQueryResponse;
import com.project.young.productservice.web.dto.ReadOptionGroupQueryResponse;
import com.project.young.productservice.web.mapper.OptionGroupQueryResponseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("queries/option-groups")
@Slf4j
public class OptionGroupQueryController {

    private final OptionGroupQueryService optionGroupQueryService;
    private final OptionGroupQueryResponseMapper optionGroupQueryResponseMapper;

    public OptionGroupQueryController(OptionGroupQueryService optionGroupQueryService,
                                      OptionGroupQueryResponseMapper optionGroupQueryResponseMapper) {
        this.optionGroupQueryService = optionGroupQueryService;
        this.optionGroupQueryResponseMapper = optionGroupQueryResponseMapper;
    }

    @GetMapping
    public ResponseEntity<ReadOptionGroupListQueryResponse> getAllActiveOptionGroups() {
        log.info("REST request to get all active option groups (catalog)");
        List<ReadOptionGroupView> views = optionGroupQueryService.getAllActiveOptionGroups();
        return ResponseEntity.ok(optionGroupQueryResponseMapper.toReadOptionGroupListQueryResponse(views));
    }

    @GetMapping("/{optionGroupId}")
    public ResponseEntity<ReadOptionGroupQueryResponse> getActiveOptionGroupDetail(
            @PathVariable("optionGroupId") UUID optionGroupId) {
        log.info("REST request to get active option group detail for optionGroupId={}", optionGroupId);
        ReadOptionGroupView view = optionGroupQueryService.getActiveOptionGroupDetail(new OptionGroupId(optionGroupId));
        return ResponseEntity.ok(optionGroupQueryResponseMapper.toReadOptionGroupQueryResponse(view));
    }
}
