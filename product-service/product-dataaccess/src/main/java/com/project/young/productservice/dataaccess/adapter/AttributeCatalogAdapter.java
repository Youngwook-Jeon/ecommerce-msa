package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.port.output.view.AttributeCategoryView;
import com.project.young.productservice.dataaccess.entity.CategoryAttributeEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.mapper.AttributeCatalogDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.AttributeDefinitionJpaRepository;
import com.project.young.productservice.dataaccess.repository.AttributeValueJpaRepository;
import com.project.young.productservice.dataaccess.repository.CategoryAttributeJpaRepository;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.AttributeValue;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
import com.project.young.productservice.domain.exception.AttributeInUseException;
import com.project.young.productservice.domain.valueobject.CategoryAttributePolicy;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class AttributeCatalogAdapter implements AttributeCatalogPort {
    private final AttributeDefinitionJpaRepository definitions;
    private final AttributeValueJpaRepository values;
    private final CategoryAttributeJpaRepository policies;
    private final CategoryJpaRepository categories;
    private final AttributeCatalogDataAccessMapper mapper;
    private final EntityManager entityManager;
    private final IdGenerator idGenerator;

    public AttributeCatalogAdapter(AttributeDefinitionJpaRepository definitions, AttributeValueJpaRepository values,
                                   CategoryAttributeJpaRepository policies, CategoryJpaRepository categories,
                                   AttributeCatalogDataAccessMapper mapper, EntityManager entityManager,
                                   IdGenerator idGenerator) {
        this.definitions = definitions;
        this.values = values;
        this.policies = policies;
        this.categories = categories;
        this.mapper = mapper;
        this.entityManager = entityManager;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<AttributeDefinition> findDefinitions() {
        return definitions.findAllByOrderByCodeAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AttributeDefinition> findDefinitionsByIds(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return definitions.findAllById(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<AttributeDefinition> findDefinition(UUID id) {
        return definitions.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<AttributeDefinition> findDefinitionForUpdate(UUID id) {
        return definitions.findLockedById(id).map(mapper::toDomain);
    }

    @Override
    public boolean hasDefinitionReferences(UUID id) {
        return values.existsByAttributeId(id) || policies.existsByAttributeId(id);
    }

    @Override
    @Transactional
    public void insertDefinition(AttributeDefinition definition) {
        var entity = mapper.toEntity(definition);
        entityManager.persist(entity);
    }

    @Override
    @Transactional
    public void updateDefinition(AttributeDefinition definition) {
        var entity = definitions.findById(definition.getId().getValue())
                .orElseThrow(() -> new AttributeNotFoundException("Attribute definition not found"));
        entity.setDisplayName(definition.getDisplayName());
        entity.setStatus(definition.getStatus());
    }

    @Override
    public void deleteDefinition(UUID id) {
        try {
            definitions.deleteById(id);
            definitions.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new AttributeInUseException();
        }
    }

    @Override
    public List<AttributeValue> findValues(UUID attributeId) {
        return values.findByAttributeIdOrderByDisplayNameAscIdAsc(attributeId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<AttributeValue> findValue(UUID attributeId, UUID valueId) {
        return values.findByIdAndAttributeId(valueId, attributeId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void insertValue(AttributeValue value) {
        var entity = mapper.toEntity(value);
        entityManager.persist(entity);
    }

    @Override
    @Transactional
    public void updateValue(AttributeValue value) {
        var entity = values.findByIdAndAttributeId(value.getId().getValue(), value.getAttributeId().getValue())
                .orElseThrow(() -> new AttributeNotFoundException("Attribute value not found"));
        entity.setDisplayName(value.getDisplayName());
    }

    @Override
    public void deleteValue(UUID valueId) {
        values.deleteById(valueId);
        values.flush();
    }

    @Override
    public Optional<AttributeCategoryView> findCategory(Long categoryId) {
        return categories.findById(categoryId).map(category -> new AttributeCategoryView(category.getId(),
                category.getStatus() == CategoryStatusEntity.DELETED));
    }

    @Override
    public List<CategoryAttributePolicy> findPoliciesByCategoryId(Long categoryId) {
        return policies.findByCategoryId(categoryId).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public CategoryAttributePolicy savePolicy(CategoryAttributePolicy policy) {
        var existing = policies.findByCategoryIdAndAttributeId(policy.categoryId(), policy.attributeId());
        var entity = existing.orElseGet(() -> {
            var created = new CategoryAttributeEntity();
            created.setId(idGenerator.generateId());
            return created;
        });
        mapper.apply(policy, entity);
        if (existing.isEmpty()) {
            entityManager.persist(entity);
        }
        return mapper.toDomain(entity);
    }

    @Override
    public void deletePolicy(Long categoryId, UUID attributeId) {
        policies.findByCategoryIdAndAttributeId(categoryId, attributeId).ifPresent(policies::delete);
        policies.flush();
    }
}
