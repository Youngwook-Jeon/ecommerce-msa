package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.exception.OptionGroupVersionConflictException;
import com.project.young.productservice.application.port.output.OptionGroupDetailsPort;
import com.project.young.productservice.dataaccess.mapper.OptionGroupDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.OptionGroupJpaRepository;
import com.project.young.productservice.domain.exception.OptionGroupNotFoundException;
import com.project.young.productservice.domain.valueobject.OptionGroupDetails;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional
public class OptionGroupDetailsAdapter implements OptionGroupDetailsPort {
    private final OptionGroupJpaRepository groups;
    private final OptionGroupDataAccessMapper mapper;

    public OptionGroupDetailsAdapter(OptionGroupJpaRepository groups, OptionGroupDataAccessMapper mapper) {
        this.groups = groups;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OptionGroupDetails> find(OptionGroupId id) {
        if (id == null) {
            throw new IllegalArgumentException("optionGroupId must not be null.");
        }
        return groups.findById(id.getValue()).map(entity -> new OptionGroupDetails(new OptionGroupId(entity.getId()),
                entity.getName(), entity.getDisplayName(), mapper.toDomainStatus(entity.getStatus()), entity.getVersion()));
    }

    @Override
    public void update(OptionGroupDetails details) {
        var entity = groups.findById(details.id().getValue())
                .orElseThrow(() -> new OptionGroupNotFoundException("Option group not found."));
        if (entity.getVersion() != details.version()) {
            throw new OptionGroupVersionConflictException(details.id().getValue(), details.version());
        }
        // Dirty checking includes @Version in the UPDATE predicate and increments it on flush.
        entity.setName(details.name());
        entity.setDisplayName(details.displayName());
        entity.setStatus(mapper.toEntityStatus(details.status()));
    }
}
