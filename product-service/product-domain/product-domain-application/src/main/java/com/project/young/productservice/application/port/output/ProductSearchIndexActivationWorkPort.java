package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationJob;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface ProductSearchIndexActivationWorkPort {
    record Work(ProductSearchIndexActivationJob job, ProductSearchIndexVersion version, String expectedIndexName,
                UUID ownerToken) {
    }

    void recoverExpired();

    Optional<Work> claim(Duration lease);

    void renew(Work work, Duration lease);

    void beginSwitch(Work work);

    void succeed(Work work);

    void failOrUnconfirm(Work work);
}
