package com.project.young.productservice.application.port.output;

import java.util.Optional;
import java.util.UUID;

public interface ProductOptionGroupVisualPort {

    void setDrivesVariantImages(UUID productId, UUID productOptionGroupId, boolean drivesVariantImages);

    Optional<UUID> findActiveVisualGroupId(UUID productId);
}
