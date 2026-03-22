package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.port.output.OptionGroupReadRepository;
import com.project.young.productservice.application.port.output.view.ReadOptionGroupView;
import com.project.young.productservice.domain.exception.OptionGroupNotFoundException;
import lombok.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OptionGroupQueryService {

    private final OptionGroupReadRepository optionGroupReadRepository;

    public OptionGroupQueryService(OptionGroupReadRepository optionGroupReadRepository) {
        this.optionGroupReadRepository = optionGroupReadRepository;
    }

    public List<ReadOptionGroupView> getAllActiveOptionGroups() {
        return optionGroupReadRepository.findAllActiveCatalogOptionGroups();
    }

    public ReadOptionGroupView getActiveOptionGroupDetail(@NonNull OptionGroupId optionGroupId) {
        return optionGroupReadRepository.findActiveCatalogOptionGroupById(optionGroupId)
                .orElseThrow(() -> new OptionGroupNotFoundException(
                        "Option group not found or not active: " + optionGroupId.getValue()));
    }

    public List<ReadOptionGroupView> getAllOptionGroupsForAdmin() {
        return optionGroupReadRepository.findAllAdminOptionGroups();
    }

    public ReadOptionGroupView getOptionGroupDetailForAdmin(@NonNull OptionGroupId optionGroupId) {
        return optionGroupReadRepository.findAdminOptionGroupById(optionGroupId)
                .orElseThrow(() -> new OptionGroupNotFoundException(
                        "Option group not found in database: " + optionGroupId.getValue()));
    }
}
