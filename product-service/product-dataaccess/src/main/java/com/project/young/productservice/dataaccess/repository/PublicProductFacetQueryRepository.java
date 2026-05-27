package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.application.dto.query.PublicProductFacetQuery;
import com.project.young.productservice.application.dto.query.PublicProductFacetType;
import com.project.young.productservice.application.dto.result.PublicProductBrandFacetValueResult;
import com.project.young.productservice.application.dto.result.PublicProductFacetResult;
import com.project.young.productservice.application.dto.result.PublicProductPriceFacetBucketResult;
import com.project.young.productservice.dataaccess.config.PublicProductSearchProperties;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static com.project.young.productservice.dataaccess.entity.QCategoryEntity.categoryEntity;
import static com.project.young.productservice.dataaccess.entity.QProductEntity.productEntity;

@Repository
@Transactional(readOnly = true)
public class PublicProductFacetQueryRepository {

    private static final List<PriceBucket> DEFAULT_PRICE_BUCKETS = List.of(
            new PriceBucket("under_25", "Under $25", null, new BigDecimal("25")),
            new PriceBucket("25_50", "$25 - $50", new BigDecimal("25"), new BigDecimal("50")),
            new PriceBucket("50_100", "$50 - $100", new BigDecimal("50"), new BigDecimal("100")),
            new PriceBucket("100_200", "$100 - $200", new BigDecimal("100"), new BigDecimal("200")),
            new PriceBucket("200_plus", "$200+", new BigDecimal("200"), null)
    );

    private final JPAQueryFactory queryFactory;
    private final PublicProductSearchProperties searchProperties;

    public PublicProductFacetQueryRepository(
            JPAQueryFactory queryFactory,
            PublicProductSearchProperties searchProperties
    ) {
        this.queryFactory = queryFactory;
        this.searchProperties = searchProperties;
    }

    public PublicProductFacetResult getFacets(PublicProductFacetQuery query) {
        Set<PublicProductFacetType> requested = Set.copyOf(query.facets());
        long totalMatching = count(buildWhere(query, false, false));

        List<PublicProductBrandFacetValueResult> brands = requested.contains(PublicProductFacetType.BRAND)
                ? loadBrandFacetValues(query)
                : List.of();
        List<PublicProductPriceFacetBucketResult> priceBuckets = requested.contains(PublicProductFacetType.PRICE)
                ? loadPriceBuckets(query)
                : List.of();

        return new PublicProductFacetResult(query.categoryId(), totalMatching, brands, priceBuckets);
    }

    private List<PublicProductBrandFacetValueResult> loadBrandFacetValues(PublicProductFacetQuery query) {
        BooleanBuilder where = buildWhere(query, true, false);
        List<Tuple> rows = queryFactory
                .select(productEntity.brand, productEntity.id.count())
                .from(productEntity)
                .innerJoin(productEntity.category, categoryEntity)
                .where(where, productEntity.brand.isNotNull(), productEntity.brand.ne(""))
                .groupBy(productEntity.brand)
                .orderBy(productEntity.id.count().desc(), productEntity.brand.asc())
                .limit(80)
                .fetch();

        Set<String> selectedBrands = Set.copyOf(query.brands());
        return rows.stream()
                .map(row -> new PublicProductBrandFacetValueResult(
                        row.get(productEntity.brand),
                        row.get(productEntity.id.count()),
                        selectedBrands.contains(row.get(productEntity.brand))
                ))
                .toList();
    }

    private List<PublicProductPriceFacetBucketResult> loadPriceBuckets(PublicProductFacetQuery query) {
        BooleanBuilder whereWithoutPrice = buildWhere(query, false, true);
        List<PriceBucketAggregate> bucketAggregates = DEFAULT_PRICE_BUCKETS.stream()
                .map(bucket -> new PriceBucketAggregate(
                        bucket,
                        new CaseBuilder()
                                .when(bucket.predicate())
                                .then(1L)
                                .otherwise(0L)
                                .sum()
                ))
                .toList();

        Expression<?>[] countExpressions = bucketAggregates.stream()
                .map(PriceBucketAggregate::countExpression)
                .toArray(Expression[]::new);

        Tuple row = queryFactory
                .select(countExpressions)
                .from(productEntity)
                .innerJoin(productEntity.category, categoryEntity)
                .where(whereWithoutPrice)
                .fetchOne();

        return bucketAggregates.stream()
                .map(aggregate -> {
                    Long count = row == null ? 0L : row.get(aggregate.countExpression());
                    return new PublicProductPriceFacetBucketResult(
                            aggregate.bucket().id(),
                            aggregate.bucket().label(),
                            aggregate.bucket().min(),
                            aggregate.bucket().max(),
                            count != null ? count : 0L
                    );
                })
                .toList();
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
            PublicProductFacetQuery query,
            boolean excludeBrandFilter,
            boolean excludePriceFilter
    ) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(productEntity.status.eq(ProductStatusEntity.ACTIVE));
        builder.and(categoryEntity.status.eq(CategoryStatusEntity.ACTIVE));
        builder.and(categoryEntity.id.eq(query.categoryId()));

        if (!excludeBrandFilter && !query.brands().isEmpty()) {
            builder.and(productEntity.brand.in(query.brands()));
        }

        PublicProductKeywordPredicates.appendKeywordPredicate(
                builder,
                query.q(),
                searchProperties.resolvedKeywordStrategy()
        );

        if (!excludePriceFilter && query.minPrice() != null) {
            builder.and(productEntity.basePrice.goe(query.minPrice()));
        }
        if (!excludePriceFilter && query.maxPrice() != null) {
            builder.and(productEntity.basePrice.loe(query.maxPrice()));
        }

        return builder;
    }

    private record PriceBucket(String id, String label, BigDecimal min, BigDecimal max) {
        BooleanExpression predicate() {
            if (min == null && max == null) {
                return productEntity.basePrice.isNotNull();
            }
            if (min == null) {
                return productEntity.basePrice.lt(max);
            }
            if (max == null) {
                return productEntity.basePrice.goe(min);
            }
            return productEntity.basePrice.goe(min).and(productEntity.basePrice.lt(max));
        }
    }

    private record PriceBucketAggregate(
            PriceBucket bucket,
            NumberExpression<Long> countExpression
    ) {
    }
}
