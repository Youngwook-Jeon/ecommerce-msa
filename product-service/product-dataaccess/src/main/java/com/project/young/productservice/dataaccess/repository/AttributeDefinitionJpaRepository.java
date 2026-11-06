package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.AttributeDefinitionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Set;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttributeDefinitionJpaRepository extends JpaRepository<AttributeDefinitionEntity, UUID> {
    List<AttributeDefinitionEntity> findAllByOrderByCodeAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM AttributeDefinitionEntity d WHERE d.id IN :ids ORDER BY d.id")
    List<AttributeDefinitionEntity> findAllForUpdate(@Param("ids") Set<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AttributeDefinitionEntity> findLockedById(UUID id);
}
