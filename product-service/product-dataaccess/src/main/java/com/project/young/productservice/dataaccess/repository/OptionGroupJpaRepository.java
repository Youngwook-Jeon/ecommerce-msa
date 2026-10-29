package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OptionGroupJpaRepository extends JpaRepository<OptionGroupEntity, UUID> {

    boolean existsByName(String name);

    boolean existsByAttributeId(UUID attributeId);

    // Lock only the parent row; a collection fetch join cannot safely lock a nullable outer-join side.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from OptionGroupEntity g where g.id = :id")
    Optional<OptionGroupEntity> findLockedById(UUID id);

    @EntityGraph(attributePaths = {"optionValues"})
    Optional<OptionGroupEntity> findAggregateById(@NonNull UUID id);

    @EntityGraph(attributePaths = {"optionValues"})
    List<OptionGroupEntity> findAllByStatusOrderByNameAsc(OptionStatusEntity status);

    @EntityGraph(attributePaths = {"optionValues"})
    Optional<OptionGroupEntity> findByIdAndStatus(UUID id, OptionStatusEntity status);

    @EntityGraph(attributePaths = {"optionValues"})
    List<OptionGroupEntity> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"optionValues"})
    List<OptionGroupEntity> findAllByIdIn(Collection<UUID> ids);
}
