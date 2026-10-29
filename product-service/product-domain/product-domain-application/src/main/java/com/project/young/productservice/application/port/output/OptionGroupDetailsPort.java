package com.project.young.productservice.application.port.output;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.domain.valueobject.OptionGroupDetails;

import java.util.Optional;

public interface OptionGroupDetailsPort {
    /** Reads parent-only state without a pessimistic lock; update uses the snapshot version. */
    Optional<OptionGroupDetails> find(OptionGroupId id);

    void update(OptionGroupDetails details);
}
