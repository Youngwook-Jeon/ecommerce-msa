package com.project.young.productservice.application.port.output;

import java.util.UUID;

public interface VariantMainImageSyncPort {

    void syncByProductOptionValueId(UUID productOptionValueId);

    void syncAllForProduct(UUID productId);

    void syncForVariant(UUID variantId);
}
