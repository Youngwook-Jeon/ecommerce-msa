package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.ProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
@Slf4j
public class ProductReadRepositoryImpl implements ProductReadRepository {

    private final ProductJpaRepository productJpaRepository;
    private final ProductDataAccessMapper productDataAccessMapper;

    public ProductReadRepositoryImpl(ProductJpaRepository productJpaRepository,
                                     ProductDataAccessMapper productDataAccessMapper) {
        this.productJpaRepository = productJpaRepository;
        this.productDataAccessMapper = productDataAccessMapper;
    }

    @Override
    public List<ReadProductView> findAllVisibleProducts() {
        return productJpaRepository.findAllVisible(ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE)
                .stream()
                .map(this::toReadProductView)
                .toList();
    }

    @Override
    public List<ReadProductView> findVisibleByCategoryId(CategoryId categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId must not be null.");
        }

        List<ProductEntity> entities = productJpaRepository.findVisibleByCategoryId(
                categoryId.getValue(),
                ProductStatusEntity.ACTIVE,
                CategoryStatusEntity.ACTIVE
        );
        log.info("Found {} products for category {}", entities.size(), categoryId);

        return entities.stream()
                .map(this::toReadProductView)
                .toList();
    }

    @Override
    public Optional<ReadProductView> findVisibleById(ProductId productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null.");
        }

        return productJpaRepository.findVisibleById(
                        productId.getValue(),
                        ProductStatusEntity.ACTIVE,
                        CategoryStatusEntity.ACTIVE
                )
                .map(this::toReadProductView);
    }

    private ReadProductView toReadProductView(ProductEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null.");

        Long categoryId = (entity.getCategory() != null)
                ? entity.getCategory().getId()
                : null;

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