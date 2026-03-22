package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.Optional;
import java.util.UUID;

public interface OptionGroupJpaRepository extends JpaRepository<OptionGroupEntity, UUID> {

    boolean existsByName(String name);

    @Override
    @EntityGraph(attributePaths = {"optionValues"})
    Optional<OptionGroupEntity> findById(@NonNull UUID id);
}
