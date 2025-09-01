package com.project.young.productservice.domain.repository;

import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.productservice.domain.entity.Brand;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {

    Brand save(Brand brand);

    List<Brand> saveAll(List<Brand> brands);

    Optional<Brand> findById(BrandId brandId);

    List<Brand> findAllById(List<BrandId> brandIds);

    List<Brand> findByStatus(String status);

    List<Brand> findByStatusIn(List<String> statuses);

    Optional<Brand> findByName(String name);

    List<Brand> findByNameContaining(String namePattern);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, BrandId brandIdToExclude);

    void deleteById(BrandId brandId);

    void updateStatusForIds(String status, List<BrandId> brandIds);

    long countByStatus(String status);

    List<Brand> findAll();
}