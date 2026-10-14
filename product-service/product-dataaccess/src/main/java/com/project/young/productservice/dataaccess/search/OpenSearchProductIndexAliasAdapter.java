package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.application.exception.ProductSearchIndexManagementException;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexActivationWorkPort.Work;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexMappingProperties;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.indices.GetAliasRequest;
import org.opensearch.client.opensearch.indices.UpdateAliasesRequest;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_MANAGEMENT_UNAVAILABLE;
import static com.project.young.productservice.application.exception.ProductSearchIndexManagementException.Code.INDEX_STATE_UNCONFIRMED;

@Slf4j
public class OpenSearchProductIndexAliasAdapter implements ProductSearchIndexAliasPort {
    private final OpenSearchClient client;
    private final ProductSearchIndexMappingProperties mapping;

    public OpenSearchProductIndexAliasAdapter(OpenSearchClient client, ProductSearchIndexMappingProperties mapping) {
        this.client = client;
        this.mapping = mapping;
    }

    @Override
    public AliasState readAlias(String aliasName) {
        try {
            var result = client.indices().getAlias(GetAliasRequest.of(r -> r.name(aliasName))).result();
            boolean plain = !result.isEmpty() && result.values().stream().allMatch(index -> {
                var alias = index.aliases().get(aliasName);
                return alias != null && alias.filter() == null && alias.routing() == null
                        && alias.searchRouting() == null && alias.indexRouting() == null;
            });
            return new AliasState(result.keySet(), plain);
        } catch (OpenSearchException exception) {
            if (exception.status() == 404) {
                try {
                    // An index occupying the alias name is not an empty alias state.
                    return new AliasState(Set.of(), !client.indices().exists(r -> r.index(aliasName)).value());
                } catch (Exception failure) {
                    throw unavailable(aliasName, failure);
                }
            }
            throw unavailable(aliasName, exception);
        } catch (IOException exception) {
            throw unavailable(aliasName, exception);
        }
    }

    @Override
    public void verifyBeforeSwitch(Work work) {
        try (var input = mapping.resource().getInputStream()) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            if (work.version().mappingVersion() != 1 || !hash.equals(work.version().mappingHash())) {
                throw new IllegalStateException("Activation target mapping is incompatible with this application");
            }
            new OpenSearchProductBulkWriter(client).verifyTarget(work.version().indexName());
            var count = client.count(c -> c.index(work.version().indexName()));
            if (count.shards().failed() > 0) {
                throw new IllegalStateException("Activation target is not fully readable");
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProductSearchIndexingException("Activation target verification failed", exception);
        }
        var expected = work.expectedIndexName() == null ? Set.<String>of() : Set.of(work.expectedIndexName());
        requireAlias(work.job().aliasName(), expected);
    }

    @Override
    public void switchAlias(Work work) {
        var request = new UpdateAliasesRequest.Builder();
        if (work.expectedIndexName() != null) {
            request.actions(a -> a.remove(r -> r.index(work.expectedIndexName()).alias(work.job().aliasName()).mustExist(true)));
        }
        request.actions(a -> a.add(r -> r.index(work.version().indexName()).alias(work.job().aliasName()).isWriteIndex(false)));
        try {
            // Exactly one submission; an uncertain response must never trigger an automatic re-submission.
            var response = client.indices().updateAliases(request.build());
            if (!response.acknowledged()) {
                throw new IllegalStateException("Alias switch was not acknowledged");
            }
            log.info("Alias switch acknowledged: jobId={}, alias={}, fromIndex={}, toIndex={}",
                    work.job().id(), work.job().aliasName(), work.expectedIndexName(), work.version().indexName());
        } catch (IOException exception) {
            throw new ProductSearchIndexingException("Alias switch transport failed", exception);
        }
    }

    @Override
    public void verifyAfterSwitch(Work work) {
        requireAlias(work.job().aliasName(), Set.of(work.version().indexName()));
    }

    private void requireAlias(String aliasName, Set<String> expected) {
        var actual = readAlias(aliasName);
        if (!actual.plain() || !actual.indices().equals(expected)) {
            throw new ProductSearchIndexManagementException(INDEX_STATE_UNCONFIRMED, "Actual alias does not match the expected index");
        }
    }

    private static ProductSearchIndexManagementException unavailable(String aliasName, Exception exception) {
        log.warn("Alias lookup failed: alias={}", aliasName, exception);
        return new ProductSearchIndexManagementException(INDEX_MANAGEMENT_UNAVAILABLE, "Alias management is unavailable", exception);
    }
}
