package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.common.domain.valueobject.ProductAttributeId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.command.SetProductSpecificationCommand;
import com.project.young.productservice.application.dto.event.ProductCatalogChangeType;
import com.project.young.productservice.application.dto.result.ProductSpecificationsResult;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.port.output.ProductSpecificationReadRepository;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.entity.ProductAttribute;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class ProductSpecificationService {
    private final ProductRepository products;
    private final ProductAttributePolicyValidator policies;
    private final ProductCatalogChangeService changes;
    private final IdGenerator ids;
    private final ProductSpecificationReadRepository reads;

    public ProductSpecificationService(ProductRepository products, ProductAttributePolicyValidator policies,
                                       ProductCatalogChangeService changes, IdGenerator ids, ProductSpecificationReadRepository reads) {
        this.products = products;
        this.policies = policies;
        this.changes = changes;
        this.ids = ids;
        this.reads = reads;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductSpecificationsResult specifications(UUID productId) {
        if (productId == null) throw new IllegalArgumentException("Product ID is required");
        return reads.findByProductId(productId);
    }

    @Transactional
    public void setSpecification(UUID productId, UUID attributeId, SetProductSpecificationCommand command, long expectedVersion) {
        if (attributeId == null || command == null || command.attributeValueId() == null) {
            throw new IllegalArgumentException("Attribute and canonical value IDs are required");
        }
        var product = findMutable(productId, expectedVersion);
        var policyContext = policies.loadAndLockPolicyContext(product, Set.of(attributeId), Set.of());
        var definitionId = new AttributeDefinitionId(attributeId);
        var existing = product.getAttributes().stream().filter(attribute -> attribute.getAttributeId().equals(definitionId)).findFirst();
        var valueId = new AttributeValueId(command.attributeValueId());
        var attribute = existing.map(current -> current.changeValue(valueId))
                .orElseGet(() -> new ProductAttribute(new ProductAttributeId(ids.generateId()), definitionId, valueId));
        product.setAttribute(attribute);
        policies.validate(product, policyContext);
        if (existing.isPresent() && existing.get().getAttributeValueId().equals(valueId)) return;
        changes.recordChange(product, ProductCatalogChangeType.SPECIFICATION_CHANGED, expectedVersion);
        products.update(product);
        log.info("Product specification set: productId={}, attributeId={}", productId, attributeId);
    }

    @Transactional
    public void removeSpecification(UUID productId, UUID attributeId, long expectedVersion) {
        if (attributeId == null) throw new IllegalArgumentException("Attribute ID is required");
        var product = findMutable(productId, expectedVersion);
        var policyContext = policies.loadAndLockPolicyContext(product, Set.of(), Set.of());
        product.removeAttribute(new AttributeDefinitionId(attributeId));
        policies.validate(product, policyContext);
        changes.recordChange(product, ProductCatalogChangeType.SPECIFICATION_CHANGED, expectedVersion);
        products.update(product);
        log.info("Product specification removed: productId={}, attributeId={}", productId, attributeId);
    }

    private Product findMutable(UUID productId, long expectedVersion) {
        if (productId == null || expectedVersion < 1)
            throw new IllegalArgumentException("Product ID and positive expectedVersion are required");
        changes.verifyVersion(productId, expectedVersion);
        var product = products.findById(new ProductId(productId))
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
        if (product.isDeleted()) throw new ProductDomainException("Cannot change specifications of a deleted product");
        return product;
    }
}
