package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.application.dto.condition.PublicProductSearchCondition;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.dataaccess.config.PublicProductSearchProperties;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.projection.PublicProductListProjection;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.project.young.productservice.dataaccess.entity.QCategoryEntity.categoryEntity;
import static com.project.young.productservice.dataaccess.entity.QProductEntity.productEntity;

@Repository
@Transactional(readOnly = true)
public class PublicProductSearchQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final PublicProductSearchProperties searchProperties;

    public PublicProductSearchQueryRepository(
            JPAQueryFactory queryFactory,
            PublicProductSearchProperties searchProperties
    ) {
        this.queryFactory = queryFactory;
        this.searchProperties = searchProperties;
    }

    /**
     * 운영 기본 경로. {@link PublicProductSearchProperties#resolvedKeywordStrategy()} 사용 (기본 {@link PublicProductKeywordSearchStrategy#NAME_BRAND}).
     */
    public Page<PublicProductListProjection> search(
            PublicProductSearchCondition condition,
            PublicProductSort sort,
            Pageable pageable
    ) {
        return search(condition, sort, pageable, searchProperties.resolvedKeywordStrategy());
    }

    /**
     * 키워드 전략을 지정해 검색. 레거시·인덱스 경로 실행 시간 비교용.
     */
    public Page<PublicProductListProjection> search(
            PublicProductSearchCondition condition,
            PublicProductSort sort,
            Pageable pageable,
            PublicProductKeywordSearchStrategy keywordStrategy
    ) {
        if (condition == null) {
            throw new IllegalArgumentException("PublicProductSearchCondition cannot be null");
        }
        if (sort == null) {
            throw new IllegalArgumentException("PublicProductSort cannot be null");
        }
        if (keywordStrategy == null) {
            throw new IllegalArgumentException("PublicProductKeywordSearchStrategy cannot be null");
        }

        BooleanBuilder where = buildWhere(condition, keywordStrategy);

        JPAQuery<PublicProductListProjection> query = queryFactory
                .select(Projections.constructor(
                        PublicProductListProjection.class,
                        productEntity.id,
                        categoryEntity.id,
                        productEntity.name,
                        productEntity.brand,
                        productEntity.mainImageUrl,
                        productEntity.basePrice
                ))
                .from(productEntity)
                .innerJoin(productEntity.category, categoryEntity)
                .where(where);

        List<OrderSpecifier<?>> orderSpecifiers = toOrderSpecifiers(sort, condition);
        query.orderBy(orderSpecifiers.toArray(OrderSpecifier[]::new));

        List<PublicProductListProjection> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> count(where));
    }

    private long count(BooleanBuilder where) {
        Long total = queryFactory
                .select(productEntity.id.count())
                .from(productEntity)
                .innerJoin(productEntity.category, categoryEntity)
                .where(where)
                .fetchOne();
        return total != null ? total : 0L;
    }

    private BooleanBuilder buildWhere(
            PublicProductSearchCondition condition,
            PublicProductKeywordSearchStrategy keywordStrategy
    ) {
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(productEntity.status.eq(ProductStatusEntity.ACTIVE));
        builder.and(categoryEntity.status.eq(CategoryStatusEntity.ACTIVE));
        builder.and(categoryEntity.id.eq(condition.categoryId()));

        List<String> brands = condition.normalizedBrands();
        if (!brands.isEmpty()) {
            builder.and(productEntity.brand.in(brands));
        }

        PublicProductKeywordPredicates.appendKeywordPredicate(
                builder,
                condition.normalizedKeyword(),
                keywordStrategy
        );

        if (condition.minPrice() != null) {
            builder.and(productEntity.basePrice.goe(condition.minPrice()));
        }
        if (condition.maxPrice() != null) {
            builder.and(productEntity.basePrice.loe(condition.maxPrice()));
        }

        return builder;
    }

    private static List<OrderSpecifier<?>> toOrderSpecifiers(
            PublicProductSort sort,
            PublicProductSearchCondition condition
    ) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        switch (sort) {
            case PRICE_ASC -> orders.add(new OrderSpecifier<>(Order.ASC, productEntity.basePrice));
            case PRICE_DESC -> orders.add(new OrderSpecifier<>(Order.DESC, productEntity.basePrice));
            case RELEVANCE -> {
                String keyword = condition.normalizedKeyword();
                if (keyword != null) {
                    orders.add(new OrderSpecifier<>(
                            Order.DESC,
                            new CaseBuilder()
                                    .when(PublicProductKeywordPredicates.nameMatchForRelevance(keyword))
                                    .then(1)
                                    .otherwise(0)
                    ));
                }
                orders.add(new OrderSpecifier<>(Order.DESC, productEntity.createdAt));
            }
            case NEWEST -> orders.add(new OrderSpecifier<>(Order.DESC, productEntity.createdAt));
        }

        orders.add(new OrderSpecifier<>(Order.DESC, productEntity.id));
        return orders;
    }
}
