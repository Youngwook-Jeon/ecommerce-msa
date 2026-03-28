package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.application.dto.condition.AdminProductSearchCondition;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.projection.AdminProductListProjection;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.project.young.productservice.dataaccess.entity.QCategoryEntity.categoryEntity;
import static com.project.young.productservice.dataaccess.entity.QProductEntity.productEntity;

@Repository
@Transactional(readOnly = true)
public class AdminProductSearchQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final ProductDataAccessMapper productDataAccessMapper;

    public AdminProductSearchQueryRepository(JPAQueryFactory queryFactory,
                                             ProductDataAccessMapper productDataAccessMapper) {
        this.queryFactory = queryFactory;
        this.productDataAccessMapper = productDataAccessMapper;
    }

    public Page<AdminProductListProjection> search(AdminProductSearchCondition condition, Pageable pageable) {
        if (condition == null) {
            throw new IllegalArgumentException("AdminProductSearchCondition cannot be null");
        }
        BooleanBuilder where = buildWhere(condition);

        JPAQuery<AdminProductListProjection> query = queryFactory
                .select(Projections.constructor(
                        AdminProductListProjection.class,
                        productEntity.id,
                        categoryEntity.id,
                        productEntity.name,
                        productEntity.description,
                        productEntity.brand,
                        productEntity.mainImageUrl,
                        productEntity.basePrice,
                        productEntity.status,
                        productEntity.conditionType
                ))
                .from(productEntity)
                .leftJoin(productEntity.category, categoryEntity)
                .where(where);

        List<OrderSpecifier<?>> orderSpecifiers = toOrderSpecifiers(pageable.getSort());
        query.orderBy(orderSpecifiers.toArray(OrderSpecifier[]::new));

        List<AdminProductListProjection> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 실제로 전체를 카운팅해야 할 경우에만 count 쿼리 실행
        return PageableExecutionUtils.getPage(content, pageable, () -> count(where));
    }

    private long count(BooleanBuilder where) {
        Long c = queryFactory
                .select(productEntity.id.count())
                .from(productEntity)
                .leftJoin(productEntity.category, categoryEntity)
                .where(where)
                .fetchOne();
        return c != null ? c : 0L;
    }

    private BooleanBuilder buildWhere(AdminProductSearchCondition condition) {
        BooleanBuilder b = new BooleanBuilder();

        if (condition.status() != null) {
            b.and(productEntity.status.eq(productDataAccessMapper.toEntityStatus(condition.status())));
        }

        String brand = condition.normalizedBrand();
        if (brand != null) {
            b.and(productEntity.brand.eq(brand));
        }

        String keyword = condition.normalizedKeyword();
        if (keyword != null) {
            String pattern = "%" + keyword.toLowerCase() + "%";
            StringExpression descriptionSafe = Expressions.stringTemplate("coalesce({0}, '')", productEntity.description);
            b.and(productEntity.name.lower().like(pattern)
                    .or(descriptionSafe.lower().like(pattern)));
        }

        if (condition.categoryId() != null) {
            b.and(categoryEntity.id.eq(condition.categoryId()));
        }

        if (!condition.includeOrphansOrDefault()) {
            b.and(categoryEntity.isNotNull());
        }

        return b;
    }

    private static List<OrderSpecifier<?>> toOrderSpecifiers(Sort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        if (sort != null && sort.isSorted()) {
            for (Sort.Order o : sort) {
                orders.add(orderSpecifier(o));
            }
        }
        if (orders.isEmpty()) {
            orders.add(new OrderSpecifier<>(Order.DESC, productEntity.createdAt));
        }
        boolean sortContainsId = sort != null && sort.stream().anyMatch(o -> "id".equals(o.getProperty()));
        if (!sortContainsId) {
            orders.add(new OrderSpecifier<>(Order.DESC, productEntity.id));
        }
        return orders;
    }

    private static OrderSpecifier<?> orderSpecifier(Sort.Order o) {
        Order direction = o.isAscending() ? Order.ASC : Order.DESC;
        String prop = o.getProperty();
        return switch (prop) {
            case "name" -> new OrderSpecifier<>(direction, productEntity.name);
            case "updatedAt" -> new OrderSpecifier<>(direction, productEntity.updatedAt);
            case "id" -> new OrderSpecifier<>(direction, productEntity.id);
            case "basePrice" -> new OrderSpecifier<>(direction, productEntity.basePrice);
            case "brand" -> new OrderSpecifier<>(direction, productEntity.brand);
            case "status" -> new OrderSpecifier<>(direction, productEntity.status);
            case "createdAt" -> new OrderSpecifier<>(direction, productEntity.createdAt);
            default -> new OrderSpecifier<>(direction, productEntity.createdAt);
        };
    }
}
