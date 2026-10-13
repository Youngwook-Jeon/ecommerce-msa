package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface ProductSearchIndexBuildWorkPort {
    record Work(ProductSearchIndexBuildJob job, ProductSearchIndexVersion version, UUID ownerToken) {
    }

    void recoverExpired();

    Optional<Work> claim(Duration lease);

    void renew(Work work, Duration lease);

    void recordBatch(Work work, long products);

    void beginVerification(Work work);

    void succeed(Work work, long indexCount);

    void fail(Work work);
}
