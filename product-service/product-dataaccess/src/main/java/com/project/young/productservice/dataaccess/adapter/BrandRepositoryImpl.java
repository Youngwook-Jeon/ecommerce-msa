package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.productservice.dataaccess.entity.BrandEntity;
import com.project.young.productservice.dataaccess.mapper.BrandDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.BrandJpaRepository;
import com.project.young.productservice.domain.entity.Brand;
import com.project.young.productservice.domain.repository.BrandRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;
    private final BrandDataAccessMapper brandDataAccessMapper;

    public BrandRepositoryImpl(BrandJpaRepository brandJpaRepository,
                               BrandDataAccessMapper brandDataAccessMapper) {
        this.brandJpaRepository = brandJpaRepository;
        this.brandDataAccessMapper = brandDataAccessMapper;
    }

    @Override
    public Brand save(Brand brand) {
        BrandEntity brandEntity = brandDataAccessMapper.brandToBrandEntity(brand);
        BrandEntity savedEntity = brandJpaRepository.save(brandEntity);
        log.debug("Brand saved with id: {}", savedEntity.getId());
        return brandDataAccessMapper.brandEntityToBrand(savedEntity);
    }

    @Override
    public List<Brand> saveAll(List<Brand> brands) {
        List<BrandEntity> brandEntities = brands.stream()
                .map(brandDataAccessMapper::brandToBrandEntity)
                .collect(Collectors.toList());

        List<BrandEntity> savedEntities = brandJpaRepository.saveAll(brandEntities);
        log.debug("Saved {} brands", savedEntities.size());

        return savedEntities.stream()
                .map(brandDataAccessMapper::brandEntityToBrand)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Brand> findById(BrandId brandId) {
        return brandJpaRepository.findById(brandId.getValue())
                .map(brandDataAccessMapper::brandEntityToBrand);
    }

    @Override
    public List<Brand> findAllById(List<BrandId> brandIds) {
        List<UUID> ids = brandIds.stream()
                .map(BrandId::getValue)
                .collect(Collectors.toList());

        return brandJpaRepository.findAllById(ids).stream()
                .map(brandDataAccessMapper::brandEntityToBrand)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByName(String name) {
        return brandJpaRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, BrandId brandIdToExclude) {
        return brandJpaRepository.existsByNameAndIdNot(name, brandIdToExclude.getValue());
    }

    @Override
    public void deleteById(BrandId brandId) {

    }

    @Override
    public List<Brand> findByStatus(String status) {
        return brandJpaRepository.findByStatus(status).stream()
                .map(brandDataAccessMapper::brandEntityToBrand)
                .collect(Collectors.toList());
    }

    @Override
    public List<Brand> findByStatusIn(List<String> statuses) {
        return List.of();
    }

    @Override
    public Optional<Brand> findByName(String name) {
        return Optional.empty();
    }

    @Override
    public List<Brand> findByNameContaining(String namePattern) {
        return List.of();
    }

    @Override
    public void updateStatusForIds(String newStatus, List<BrandId> brandIds) {
        List<UUID> ids = brandIds.stream()
                .map(BrandId::getValue)
                .collect(Collectors.toList());

        int updatedCount = brandJpaRepository.updateStatusByIdIn(newStatus, ids);
        log.info("Updated status to '{}' for {} brands", newStatus, updatedCount);
    }

    @Override
    public long countByStatus(String status) {
        return 0;
    }

    @Override
    public List<Brand> findAll() {
        return List.of();
    }
}