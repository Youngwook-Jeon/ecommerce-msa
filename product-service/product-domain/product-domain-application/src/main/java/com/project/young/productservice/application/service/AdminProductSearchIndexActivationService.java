package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.command.RegisterProductSearchIndexActivationCommand;
import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationMetadataPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INVALID_INDEX_MANAGEMENT_REQUEST;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_NOT_READY;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.ACTIVE_INDEX_CHANGED;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_STATE_UNCONFIRMED;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_ACTIVATION_NOT_FOUND;

@Service
public class AdminProductSearchIndexActivationService {
    private final ProductSearchIndexActivationMetadataPort metadata;
    private final ProductSearchIndexAliasPort aliases;

    public AdminProductSearchIndexActivationService(ProductSearchIndexActivationMetadataPort metadata, ProductSearchIndexAliasPort aliases) {
        this.metadata = metadata;
        this.aliases = aliases;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProductSearchIndexActivationJob register(RegisterProductSearchIndexActivationCommand command) {
        validateRegistration(command);
        // Replays must work even after the alias changed or OpenSearch became unavailable.
        if (metadata.findRequest(command.requestedBy(), command.idempotencyKey()).isPresent()) {
            return metadata.register(command);
        }
        try {
            var context = metadata.context(command.indexVersionId());
            if (context.target().status() != ProductSearchIndexStatus.READY) {
                throw new ProductSearchIndexManagementException(INDEX_NOT_READY, "Only a READY index can be activated");
            }
            if (!Objects.equals(command.expectedActiveVersionId(), context.active() == null ? null : context.active().id())) {
                throw new ProductSearchIndexManagementException(ACTIVE_INDEX_CHANGED, "Active index version changed");
            }
            var actual = aliases.readAlias(context.aliasName());
            var expected = context.active() == null ? Set.<String>of() : Set.of(context.active().indexName());
            if (!actual.plain() || !actual.indices().equals(expected)) {
                throw new ProductSearchIndexManagementException(INDEX_STATE_UNCONFIRMED, "Alias and index metadata do not agree");
            }
        } catch (ProductSearchIndexManagementException failure) {
            // Another identical request may have committed during the preflight checks.
            if (metadata.findRequest(command.requestedBy(), command.idempotencyKey()).isPresent()) {
                return metadata.register(command);
            }
            throw failure;
        }
        return metadata.register(command);
    }

    public ProductSearchIndexActivationJob getJob(UUID id) {
        if (id == null) {
            throw invalid("jobId is required");
        }
        return metadata.findJob(id).orElseThrow(() -> new ProductSearchIndexManagementException(
                INDEX_ACTIVATION_NOT_FOUND, "Index activation job was not found"));
    }

    private static void validateRegistration(RegisterProductSearchIndexActivationCommand command) {
        if (command == null || command.indexVersionId() == null) {
            throw invalid("Target index version is required");
        }
        if (command.indexVersionId().equals(command.expectedActiveVersionId())) {
            throw invalid("Target version must differ from expected active version");
        }
        if (command.requestedBy() == null || command.requestedBy().isBlank() || command.requestedBy().length() > 255) {
            throw invalid("Invalid administrator subject");
        }
        if (command.idempotencyKey() == null || !command.idempotencyKey().matches("[A-Za-z0-9._:-]{1,128}")) {
            throw invalid("Invalid idempotency key");
        }
        if (!command.catalogWritesPaused()) {
            throw invalid("Catalog writes must remain paused until activation completes");
        }
    }

    private static ProductSearchIndexManagementException invalid(String message) {
        return new ProductSearchIndexManagementException(INVALID_INDEX_MANAGEMENT_REQUEST, message);
    }
}
