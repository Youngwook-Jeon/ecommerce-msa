package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.BrandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BrandJpaRepository extends JpaRepository<BrandEntity, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    List<BrandEntity> findByStatus(String status);

    @Modifying
    @Query("UPDATE BrandEntity b SET b.status = :status WHERE b.id IN :ids")
    int updateStatusByIdIn(@Param("status") String status, @Param("ids") List<UUID> ids);

    @Query("SELECT b FROM BrandEntity b WHERE b.status <> 'DELETED' ORDER BY b.name")
    List<BrandEntity> findAllActive();
}