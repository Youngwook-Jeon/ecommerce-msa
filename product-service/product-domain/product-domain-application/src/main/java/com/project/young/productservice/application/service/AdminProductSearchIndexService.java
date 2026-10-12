package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexBuildCommand;
import com.project.young.productservice.application.dto.result.ProductSearchIndexVersionPageResult;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.port.output.ProductSearchIndexMetadataPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INVALID_INDEX_MANAGEMENT_REQUEST;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_BUILD_NOT_FOUND;

@Service
public class AdminProductSearchIndexService {
    private final ProductSearchIndexMetadataPort metadata;

    public AdminProductSearchIndexService(ProductSearchIndexMetadataPort metadata) {
        this.metadata = metadata;
    }

    public ProductSearchIndexBuildJob register(RegisterProductSearchIndexBuildCommand command) {
        if (command == null || command.requestedBy() == null || command.requestedBy().isBlank()
                || command.requestedBy().length() > 255 || command.idempotencyKey() == null
                || !command.idempotencyKey().matches("[A-Za-z0-9._:-]{1,128}")
                || command.batchSize() < 1 || command.batchSize() > 1000 || !command.catalogWritesPaused()) {
            throw new ProductSearchIndexManagementException(INVALID_INDEX_MANAGEMENT_REQUEST, "Invalid index build request");
        }
        return metadata.register(command);
    }

    public ProductSearchIndexBuildJob getJob(UUID jobId) {
        if (jobId == null) {
            throw new ProductSearchIndexManagementException(INVALID_INDEX_MANAGEMENT_REQUEST, "jobId is required");
        }
        return metadata.findJob(jobId).orElseThrow(() -> new ProductSearchIndexManagementException(
                INDEX_BUILD_NOT_FOUND, "Index build job was not found"));
    }

    public ProductSearchIndexVersionPageResult listVersions(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ProductSearchIndexManagementException(INVALID_INDEX_MANAGEMENT_REQUEST, "Invalid index version page");
        }
        return metadata.listVersions(page, size);
    }
}
