package com.project.young.productservice.dataaccess.repository;

/**
 * Public product keyword ({@code q}) predicate variant.
 * <ul>
 *   <li>{@link #NAME_BRAND} — 운영 기본. name+brand 합친 텍스트 LIKE, 단일 {@code pg_trgm} GIN.</li>
 *   <li>{@link #NAME_DESCRIPTION_LEGACY} — 레거시 비교용. name·description LIKE (description은 인덱스 없음).</li>
 * </ul>
 */
public enum PublicProductKeywordSearchStrategy {

    NAME_BRAND,
    NAME_DESCRIPTION_LEGACY
}
