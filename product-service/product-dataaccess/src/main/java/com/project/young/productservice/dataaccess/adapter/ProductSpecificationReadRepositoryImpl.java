package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.result.ProductSpecificationResult;
import com.project.young.productservice.application.dto.result.ProductSpecificationsResult;
import com.project.young.productservice.application.port.output.ProductSpecificationReadRepository;
import com.project.young.productservice.dataaccess.catalog.ProductCatalogRevisionStore;
import com.project.young.productservice.dataaccess.entity.QAttributeDefinitionEntity;
import com.project.young.productservice.dataaccess.entity.QAttributeValueEntity;
import com.project.young.productservice.dataaccess.entity.QProductAttributeEntity;
import com.project.young.productservice.dataaccess.mapper.AttributeCatalogDataAccessMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProductSpecificationReadRepositoryImpl implements ProductSpecificationReadRepository {
    private final JPAQueryFactory query;
    private final ProductCatalogRevisionStore versions;
    private final AttributeCatalogDataAccessMapper mapper;

    public ProductSpecificationReadRepositoryImpl(JPAQueryFactory query, ProductCatalogRevisionStore versions,
                                                 AttributeCatalogDataAccessMapper mapper) {
        this.query = query;
        this.versions = versions;
        this.mapper = mapper;
    }

    @Override
    public ProductSpecificationsResult findByProductId(UUID productId) {
        long version = versions.current(productId);
        var specification = QProductAttributeEntity.productAttributeEntity;
        var definition = QAttributeDefinitionEntity.attributeDefinitionEntity;
        var value = QAttributeValueEntity.attributeValueEntity;
        var rows = query.select(definition, value).from(specification)
                .join(definition).on(definition.id.eq(specification.attributeId))
                .join(value).on(value.id.eq(specification.attributeValueId).and(value.attributeId.eq(specification.attributeId)))
                .where(specification.product.id.eq(productId)).orderBy(definition.code.asc()).fetch();
        var results = rows.stream().map(row -> {
            var definitionEntity = row.get(definition);
            var valueEntity = row.get(value);
            if (definitionEntity == null || valueEntity == null) {
                throw new IllegalStateException("Product specification query returned an incomplete attribute definition or value");
            }
            return new ProductSpecificationResult(mapper.toDomain(definitionEntity), mapper.toDomain(valueEntity));
        }).toList();
        return new ProductSpecificationsResult(productId, version, results);
    }
}
