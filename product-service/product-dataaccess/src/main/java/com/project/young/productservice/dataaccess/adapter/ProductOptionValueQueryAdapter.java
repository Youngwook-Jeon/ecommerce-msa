package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.ProductOptionValueQueryPort;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class ProductOptionValueQueryAdapter implements ProductOptionValueQueryPort {

    private final ProductOptionValueJpaRepository productOptionValueJpaRepository;

    public ProductOptionValueQueryAdapter(ProductOptionValueJpaRepository productOptionValueJpaRepository) {
        this.productOptionValueJpaRepository = productOptionValueJpaRepository;
    }

    @Override
    public boolean existsOwnedByProduct(UUID productId, UUID productOptionValueId) {
        return productOptionValueJpaRepository.existsByIdAndProductOptionGroup_Product_Id(
                productOptionValueId,
                productId
        );
    }

    @Override
    public Optional<UUID> findProductIdByProductOptionValueId(UUID productOptionValueId) {
        return productOptionValueJpaRepository.findProductIdByProductOptionValueId(productOptionValueId);
    }

    @Override
    public Optional<UUID> findProductOptionGroupIdByProductOptionValueId(UUID productOptionValueId) {
        return productOptionValueJpaRepository.findProductOptionGroupIdByProductOptionValueId(productOptionValueId);
    }
}
