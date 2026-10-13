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
        validateRegistration(command);
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

    private static void validateRegistration(RegisterProductSearchIndexBuildCommand command) {
        if (command == null) {
            throw invalidRequest("Index build request is required");
        }
        validateRequestedBy(command.requestedBy());
        validateIdempotencyKey(command.idempotencyKey());
        validateBatchSize(command.batchSize());
        validateCatalogWritesPaused(command.catalogWritesPaused());
    }

    private static void validateRequestedBy(String requestedBy) {
        if (requestedBy == null || requestedBy.isBlank() || requestedBy.length() > 255) {
            throw invalidRequest("requestedBy must be nonblank and at most 255 characters");
        }
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw invalidRequest("idempotencyKey must contain 1 to 128 permitted ASCII characters");
        }
    }

    private static void validateBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw invalidRequest("batchSize must be between 1 and 1000");
        }
    }

    private static void validateCatalogWritesPaused(boolean catalogWritesPaused) {
        if (!catalogWritesPaused) {
            throw invalidRequest("Catalog writes must be paused before registering an index build");
        }
    }

    private static ProductSearchIndexManagementException invalidRequest(String message) {
        return new ProductSearchIndexManagementException(INVALID_INDEX_MANAGEMENT_REQUEST, message);
    }

}
