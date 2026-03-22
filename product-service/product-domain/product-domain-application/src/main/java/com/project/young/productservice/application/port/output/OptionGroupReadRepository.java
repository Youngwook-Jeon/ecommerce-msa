package com.project.young.productservice.application.port.output;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.port.output.view.ReadOptionGroupView;

import java.util.List;
import java.util.Optional;

public interface OptionGroupReadRepository {

    List<ReadOptionGroupView> findAllActiveCatalogOptionGroups();

    Optional<ReadOptionGroupView> findActiveCatalogOptionGroupById(OptionGroupId optionGroupId);

    List<ReadOptionGroupView> findAllAdminOptionGroups();

    Optional<ReadOptionGroupView> findAdminOptionGroupById(OptionGroupId optionGroupId);
}
