package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OptionValueJpaRepository extends JpaRepository<OptionValueEntity, UUID> {
    boolean existsByAttributeValueId(UUID attributeValueId);
}
