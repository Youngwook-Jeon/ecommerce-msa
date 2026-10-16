package com.project.young.productservice.application.port.output;

import java.util.UUID;

public interface ProductSearchSynchronizationPort {
    void synchronize(UUID productId, Long eventRevision);
}
