package com.project.young.productservice.dataaccess.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.LongConsumer;

/** Manual initial-load pipeline. Catalog writes must be stopped for the entire run. */
@Slf4j
public class ProductSearchInitialLoader {

    public static final int DEFAULT_BATCH_SIZE = 500;
    private final ProductSearchIndexBatchReader reader;
    private final OpenSearchProductBulkWriter writer;

    public ProductSearchInitialLoader(ProductSearchIndexBatchReader reader, OpenSearchProductBulkWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result load(String targetIndex, int batchSize) {
        return load(targetIndex, batchSize, () -> { }, products -> { });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result load(String targetIndex, int batchSize, Runnable checkpoint, LongConsumer completedBatch) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        checkpoint.run();
        writer.verifyTarget(targetIndex);
        UUID afterId = null;
        long indexed = 0;
        long batches = 0;
        log.info("Product initial load started: index={}, batchSize={}", targetIndex, batchSize);
        try {
            while (true) {
                checkpoint.run();
                var documents = reader.read(afterId, batchSize);
                if (documents.isEmpty()) {
                    break;
                }
                UUID next = UUID.fromString(documents.getLast().id());
                if (next.equals(afterId)) {
                    throw new ProductSearchIndexingException("Product batch cursor did not advance");
                }
                writer.write(targetIndex, documents, checkpoint);
                completedBatch.accept(documents.size());
                indexed += documents.size();
                batches++;
                afterId = next;
                log.info("Product initial load batch completed: index={}, batch={}, indexed={}, afterId={}",
                        targetIndex, batches, indexed, afterId);
            }
            log.info("Product initial load completed: index={}, batches={}, indexed={}", targetIndex, batches, indexed);
            return new Result(indexed, batches);
        } catch (RuntimeException exception) {
            log.error("Product initial load failed: index={}, batches={}, indexed={}, afterId={}, exceptionType={}",
                    targetIndex, batches, indexed, afterId, exception.getClass().getSimpleName(), exception);
            throw exception;
        }
    }

    public record Result(long indexedProducts, long batches) {
    }
}
