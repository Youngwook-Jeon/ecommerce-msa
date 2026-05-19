package com.project.young.productservice.dataaccess.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;

import static com.project.young.productservice.dataaccess.entity.QProductEntity.productEntity;

/**
 * Keyword ({@code q}) LIKE predicates for {@link PublicProductSearchQueryRepository}.
 */
final class PublicProductKeywordPredicates {

    /**
     * Must match {@code V5__product_keyword_trgm_indexes.sql} GIN index expression exactly.
     */
    static final String NAME_BRAND_SEARCH_SQL =
            "lower(coalesce({0}, '')) || ' ' || lower(coalesce({1}, ''))";

    private PublicProductKeywordPredicates() {
    }

    static String nameBrandSearchExpressionForTable(String tableAlias) {
        return "lower(coalesce(" + tableAlias + ".name, '')) || ' ' || lower(coalesce(" + tableAlias + ".brand, ''))";
    }

    static String nameBrandSearchLikePredicate(String tableAlias) {
        return "(" + nameBrandSearchExpressionForTable(tableAlias) + ") LIKE :pattern";
    }

    static String likePattern(String normalizedKeyword) {
        return "%" + normalizedKeyword.toLowerCase() + "%";
    }

    static void appendKeywordPredicate(
            BooleanBuilder builder,
            String normalizedKeyword,
            PublicProductKeywordSearchStrategy strategy
    ) {
        if (normalizedKeyword == null) {
            return;
        }
        builder.and(keywordPredicate(normalizedKeyword, strategy));
    }

    static BooleanExpression keywordPredicate(
            String normalizedKeyword,
            PublicProductKeywordSearchStrategy strategy
    ) {
        String pattern = likePattern(normalizedKeyword);
        return switch (strategy) {
            case NAME_BRAND -> nameBrandSearchTextLike(pattern);
            case NAME_DESCRIPTION_LEGACY -> productEntity.name.lower().like(pattern)
                    .or(productEntity.description.lower().like(pattern));
        };
    }

    static Predicate nameMatchForRelevance(String normalizedKeyword) {
        return productEntity.name.lower().like(likePattern(normalizedKeyword));
    }

    private static BooleanExpression nameBrandSearchTextLike(String pattern) {
        return Expressions.stringTemplate(NAME_BRAND_SEARCH_SQL, productEntity.name, productEntity.brand)
                .like(pattern);
    }
}
