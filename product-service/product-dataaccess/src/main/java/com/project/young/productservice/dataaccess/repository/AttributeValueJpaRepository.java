package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.AttributeValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttributeValueJpaRepository extends JpaRepository<AttributeValueEntity, UUID> {
    boolean existsByAttributeId(UUID attributeId);

    List<AttributeValueEntity> findByAttributeIdOrderByDisplayNameAscIdAsc(UUID attributeId);

    Optional<AttributeValueEntity> findByIdAndAttributeId(UUID id, UUID attributeId);
}
