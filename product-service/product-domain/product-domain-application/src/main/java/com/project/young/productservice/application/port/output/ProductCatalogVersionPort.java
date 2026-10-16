package com.project.young.productservice.application.port.output;

import java.util.UUID;

public interface ProductCatalogVersionPort {
    void verifyVersion(UUID productId, long expectedVersion);
}
