package com.project.young.productservice.dataaccess.search;

import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.OperationType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
public class OpenSearchProductBulkWriter {

    private static final int MAX_ATTEMPTS = 3;
    private final OpenSearchClient client;

    public OpenSearchProductBulkWriter(OpenSearchClient client) {
        this.client = client;
    }

    public void verifyTarget(String index) {
        if (index == null || !index.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("An explicit physical index name is required");
        }
        try {
            if (!client.indices().get(r -> r.index(index)).result().keySet().equals(Set.of(index))) {
                throw new ProductSearchIndexingException("Target must be an existing physical index, not an alias");
            }
        } catch (IOException | OpenSearchException exception) {
            throw new ProductSearchIndexingException("Could not verify target index", exception);
        }
    }

    public void write(String index, List<OpenSearchProductDocument> documents) {
        write(index, documents, () -> { });
    }

    public void write(String index, List<OpenSearchProductDocument> documents, Runnable beforeAttempt) {
        if (documents.stream().anyMatch(d -> d.catalogRevision() < 1)) {
            throw new IllegalArgumentException("Search revision must be positive");
        }
        List<OpenSearchProductDocument> pending = List.copyOf(documents);
        for (int attempt = 1; !pending.isEmpty(); attempt++) {
            beforeAttempt.run();
            List<OpenSearchProductDocument> retry;
            try {
                var request = new BulkRequest.Builder().refresh(Refresh.False);
                pending.forEach(document -> request.operations(o -> o.index(i -> i.index(index)
                        .id(document.id()).version(document.catalogRevision()).versionType(VersionType.External).document(document))));
                retry = failedDocuments(index, pending, client.bulk(request.build()), attempt);
            } catch (IOException exception) {
                log.warn("Product bulk transport failed: index={}, attempt={}, documentCount={}",
                        index, attempt, pending.size(), exception);
                if (attempt == MAX_ATTEMPTS) {
                    throw new ProductSearchIndexingException("Bulk transport retries exhausted", exception);
                }
                retry = pending;
            } catch (OpenSearchException exception) {
                log.warn("Product bulk request failed: index={}, attempt={}, documentCount={}, status={}",
                        index, attempt, pending.size(), exception.status(), exception);
                if (!retryable(exception.status()) || attempt == MAX_ATTEMPTS) {
                    throw new ProductSearchIndexingException("Bulk request failed", exception);
                }
                retry = pending;
            }
            if (retry.isEmpty()) {
                return;
            }
            if (attempt == MAX_ATTEMPTS) {
                throw new ProductSearchIndexingException("Bulk item retries exhausted");
            }
            log.warn("Product bulk retry: index={}, attempt={}, documentCount={}", index, attempt, retry.size());
            pause(attempt);
            pending = retry;
        }
    }

    private static List<OpenSearchProductDocument> failedDocuments(String index,
            List<OpenSearchProductDocument> documents, BulkResponse response, int attempt) {
        if (response.items().size() != documents.size()) {
            throw new ProductSearchIndexingException("Incomplete bulk response");
        }
        List<OpenSearchProductDocument> retry = new ArrayList<>();
        boolean versionConflict = false;
        for (int i = 0; i < documents.size(); i++) {
            var item = response.items().get(i);
            var document = documents.get(i);
            if (!document.id().equals(item.id()) || !index.equals(item.index()) || item.operationType() != OperationType.Index) {
                throw new ProductSearchIndexingException("Bulk response does not match requested documents");
            }
            if (item.status() >= 200 && item.status() < 300 && item.error() == null) {
                continue;
            }
            if (item.status() == 409 && item.error() != null && "version_conflict_engine_exception".equals(item.error().type())) {
                versionConflict = true;
                continue;
            }
            String errorType = item.error() == null ? null : item.error().type();
            String causeType = item.error() == null || item.error().causedBy() == null ? null : item.error().causedBy().type();
            if (!retryable(item.status())) {
                log.error("Product bulk item failed: index={}, productId={}, attempt={}, status={}, errorType={}, causeType={}",
                        index, document.id(), attempt, item.status(), errorType, causeType);
                throw new ProductSearchIndexingException("Nonretryable bulk item failure");
            }
            log.warn("Product bulk item will retry: index={}, productId={}, attempt={}, status={}, errorType={}, causeType={}",
                    index, document.id(), attempt, item.status(), errorType, causeType);
            retry.add(document);
        }
        if (response.errors() && retry.isEmpty() && !versionConflict) {
            throw new ProductSearchIndexingException("Inconsistent bulk error response");
        }
        return retry;
    }

    private static boolean retryable(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(100L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProductSearchIndexingException("Bulk retry interrupted", exception);
        }
    }
}
