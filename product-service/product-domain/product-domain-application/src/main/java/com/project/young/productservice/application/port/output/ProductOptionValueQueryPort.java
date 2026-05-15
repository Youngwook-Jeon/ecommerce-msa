package com.project.young.productservice.application.port.output;

import java.util.Optional;
import java.util.UUID;

public interface ProductOptionValueQueryPort {

    boolean existsOwnedByProduct(UUID productId, UUID productOptionValueId);

    Optional<UUID> findProductIdByProductOptionValueId(UUID productOptionValueId);

    Optional<UUID> findProductOptionGroupIdByProductOptionValueId(UUID productOptionValueId);
}
