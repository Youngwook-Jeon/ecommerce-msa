package com.project.young.productservice.dataaccess.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static com.project.young.productservice.dataaccess.entity.QAttributeValueEntity.attributeValueEntity;

@Repository
@Transactional(readOnly = true)
public class AttributeValueQueryRepository {
    private final JPAQueryFactory queryFactory;

    public AttributeValueQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public Set<UUID> findValueIds(UUID attributeId, Set<UUID> valueIds) {
        if (valueIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(queryFactory.select(attributeValueEntity.id)
                .from(attributeValueEntity)
                .where(attributeValueEntity.attributeId.eq(attributeId), attributeValueEntity.id.in(valueIds))
                .fetch());
    }
}
