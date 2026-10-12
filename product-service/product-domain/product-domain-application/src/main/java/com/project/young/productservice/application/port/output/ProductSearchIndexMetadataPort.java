package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexBuildCommand;
import com.project.young.productservice.application.dto.result.ProductSearchIndexVersionPageResult;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;

import java.util.Optional;
import java.util.UUID;

public interface ProductSearchIndexMetadataPort {
    ProductSearchIndexBuildJob register(RegisterProductSearchIndexBuildCommand command);

    Optional<ProductSearchIndexBuildJob> findJob(UUID jobId);

    ProductSearchIndexVersionPageResult listVersions(int page, int size);
}
