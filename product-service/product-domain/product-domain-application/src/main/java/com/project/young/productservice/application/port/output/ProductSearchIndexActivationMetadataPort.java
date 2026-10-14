package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexActivationCommand;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;

import java.util.Optional;
import java.util.UUID;

public interface ProductSearchIndexActivationMetadataPort {
    record Context(ProductSearchIndexVersion target, ProductSearchIndexVersion active, String aliasName) {
    }

    Context context(UUID indexVersionId);

    Optional<ProductSearchIndexActivationJob> findRequest(String requestedBy, String key);

    ProductSearchIndexActivationJob register(RegisterProductSearchIndexActivationCommand command);

    Optional<ProductSearchIndexActivationJob> findJob(UUID id);
}
