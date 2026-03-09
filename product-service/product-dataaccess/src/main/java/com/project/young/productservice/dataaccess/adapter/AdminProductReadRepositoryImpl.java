package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.AdminProductSearchCondition;
import com.project.young.productservice.application.port.output.AdminProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.AdminProductJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional(readOnly = true)
public class AdminProductReadRepositoryImpl implements AdminProductReadRepository {

    private final AdminProductJpaRepository adminProductJpaRepository;
    private final ProductDataAccessMapper productDataAccessMapper;

    public AdminProductReadRepositoryImpl(AdminProductJpaRepository adminProductJpaRepository,
                                          ProductDataAccessMapper productDataAccessMapper) {
        this.adminProductJpaRepository = adminProductJpaRepository;
        this.productDataAccessMapper = productDataAccessMapper;
    }

    @Override
    public AdminProductSearchResult search(AdminProductSearchCondition condition,
                                           int page,
                                           int size,
                                           String sortProperty,
                                           boolean ascending) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(size, 1),
                Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC,
                        sortProperty != null && !sortProperty.isBlank() ? sortProperty : "createdAt")
        );

        Page<ProductEntity> entityPage = adminProductJpaRepository.searchAdminProducts(
                condition.categoryId(),
                condition.includeOrphansOrDefault(),
                condition.status() != null ? productDataAccessMapper.toEntityStatus(condition.status()) : null,
                condition.normalizedBrand(),
                condition.normalizedKeyword(),
                pageable
        );

        List<ReadProductView> views = entityPage.getContent().stream()
                .map(this::toReadProductView)
                .toList();

        return new AdminProductSearchResult(
                views,
                entityPage.getNumber(),
                entityPage.getSize(),
                entityPage.getTotalElements(),
                entityPage.getTotalPages()
        );
    }

    private ReadProductView toReadProductView(ProductEntity entity) {
        Long categoryId = entity.getCategory() != null ? entity.getCategory().getId() : null;
        return ReadProductView.builder()
                .id(entity.getId())
                .categoryId(categoryId)
                .name(entity.getName())
                .description(entity.getDescription())
                .brand(entity.getBrand())
                .mainImageUrl(entity.getMainImageUrl())
                .basePrice(entity.getBasePrice())
                .status(productDataAccessMapper.toDomainStatus(entity.getStatus()))
                .conditionType(productDataAccessMapper.toDomainConditionType(entity.getConditionType()))
                .build();
    }
}

