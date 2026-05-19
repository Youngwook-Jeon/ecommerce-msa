package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.application.dto.condition.PublicProductSearchCondition;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.dataaccess.config.ProductDataAccessConfig;
import com.project.young.productservice.dataaccess.config.PublicProductSearchProperties;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.projection.PublicProductListProjection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 키워드 검색 전략별 실행 시간·EXPLAIN 비교 (수동 실행).
 *
 * <pre>
 * cd product-dataaccess
 * RUN_KEYWORD_BENCHMARK=true mvn test -Dtest=PublicProductKeywordSearchBenchmarkIT
 *
 * Optional:
 *   BENCHMARK_PRODUCT_COUNT=50000 (default 2000)
 *   BENCHMARK_REPORT_DIR=../../benchmark-reports  (default, ecommerce-msa 기준)
 *
 * Selectivity table only:
 *   RUN_KEYWORD_BENCHMARK=true mvn test -Dtest=PublicProductKeywordSearchBenchmarkIT#exportSelectivityComparisonReport
 * </pre>
 *
 */
@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = PublicProductKeywordSearchBenchmarkIT.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_KEYWORD_BENCHMARK", matches = "true")
class PublicProductKeywordSearchBenchmarkIT {

    private static final int SEED_PRODUCT_COUNT = resolveSeedCount();
    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURE_ITERATIONS = 10;

    /** 서로 다른 소수 모듈로 키워드별 매칭 집합을 겹치지 않게 분리. */
    private static final List<SelectivityTier> SELECTIVITY_TIERS = List.of(
            new SelectivityTier("~0.002% (1건)", "xqzbenchuniq"),
            new SelectivityTier("~0.1%", "kwt0p1"),
            new SelectivityTier("~1%", "kwt1"),
            new SelectivityTier("~2%", "kwt2"),
            new SelectivityTier("~5%", "kwt5"),
            new SelectivityTier("~9%", "kwt9"),
            new SelectivityTier("~9% (데님·기존 시드)", "데님")
    );

    private record SelectivityTier(String label, String keyword) {
    }

    private record ExplainSnapshot(String plan, boolean seqScan, boolean usesGin, double executionMs) {
    }

    private enum ExplainPlanMode {
        /** status + category + keyword (운영 유사). */
        CATEGORY_SCOPED,
        /** 키워드만, 플래너 기본. */
        ISOLATED_DEFAULT,
        /** 키워드만, {@code enable_seqscan = off}. */
        ISOLATED_GIN_FORCED,
        /** 키워드만, bitmap/index scan 비활성 → Seq Scan 유도. */
        ISOLATED_SEQ_SCAN_FORCED
    }

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private PublicProductSearchQueryRepository publicProductSearchQueryRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManager entityManager;

    private Long categoryId;
    private String keyword;

    @BeforeEach
    void seedLargeCatalog() {
        productJpaRepository.deleteAll();
        testEntityManager.flush();

        CategoryEntity category = persistCategory("벤치마크");
        categoryId = category.getId();
        keyword = "데님";

        Instant now = Instant.now();
        for (int i = 0; i < SEED_PRODUCT_COUNT; i++) {
            String name = resolveBenchmarkProductName(i);
            String description = (i % 23 == 0)
                    ? "설명에만 데님 포함 " + i
                    : "일반 설명 " + i;
            String brand = (i % 31 == 0) ? "데님브랜드" + i : "브랜드" + (i % 50);

            testEntityManager.persist(ProductEntity.builder()
                    .id(UUID.randomUUID())
                    .category(category)
                    .name(name)
                    .description(description)
                    .basePrice(BigDecimal.valueOf(10_000 + i))
                    .status(ProductStatusEntity.ACTIVE)
                    .conditionType(ConditionTypeEntity.NEW)
                    .brand(brand)
                    .mainImageUrl("https://example.com/" + i + ".jpg")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
        Instant rareNow = Instant.now();
        testEntityManager.persist(ProductEntity.builder()
                .id(UUID.randomUUID())
                .category(category)
                .name("벤치 전용 희귀 키워드 xqzbenchuniq")
                .description("rare")
                .basePrice(BigDecimal.valueOf(99_999))
                .status(ProductStatusEntity.ACTIVE)
                .conditionType(ConditionTypeEntity.NEW)
                .brand("rare-brand")
                .mainImageUrl("https://example.com/rare.jpg")
                .createdAt(rareNow)
                .updatedAt(rareNow)
                .build());

        testEntityManager.flush();
        testEntityManager.clear();

        entityManager.createNativeQuery("ANALYZE products").executeUpdate();
    }

    @Test
    @DisplayName("NAME_BRAND vs NAME_DESCRIPTION_LEGACY 실행 시간·EXPLAIN 비교")
    void compareKeywordSearchStrategies() {
        PublicProductSearchCondition condition = new PublicProductSearchCondition(
                categoryId, keyword, null, null, null);
        var pageable = PageRequest.of(0, 24);

        warmUp(condition, pageable);

        long legacyMs = measureMillis(
                () -> publicProductSearchQueryRepository.search(
                        condition,
                        PublicProductSort.NEWEST,
                        pageable,
                        PublicProductKeywordSearchStrategy.NAME_DESCRIPTION_LEGACY
                )
        );

        long nameBrandMs = measureMillis(
                () -> publicProductSearchQueryRepository.search(
                        condition,
                        PublicProductSort.NEWEST,
                        pageable,
                        PublicProductKeywordSearchStrategy.NAME_BRAND
                )
        );

        System.out.println("=== Public product keyword search benchmark ===");
        System.out.println("seedProducts=" + SEED_PRODUCT_COUNT + ", keyword=" + keyword);
        System.out.println("NAME_DESCRIPTION_LEGACY avg ms (" + MEASURE_ITERATIONS + " runs, Hibernate end-to-end): " + legacyMs);
        System.out.println("NAME_BRAND avg ms (" + MEASURE_ITERATIONS + " runs, Hibernate end-to-end): " + nameBrandMs);
        System.out.println();

        String legacyPlan = explainCountPlan(PublicProductKeywordSearchStrategy.NAME_DESCRIPTION_LEGACY, false);
        String nameBrandPlan = explainCountPlan(PublicProductKeywordSearchStrategy.NAME_BRAND, false);

        System.out.println("--- EXPLAIN NAME_DESCRIPTION_LEGACY (count, category scoped) ---");
        System.out.println(legacyPlan);
        printPlanDiagnosis("LEGACY", legacyPlan);
        System.out.println();
        System.out.println("--- EXPLAIN NAME_BRAND (count, category scoped) ---");
        System.out.println(nameBrandPlan);
        printPlanDiagnosis("NAME_BRAND", nameBrandPlan);
        System.out.println();
        System.out.println("--- EXPLAIN NAME_BRAND (count, NO category filter — GIN이 보이기 쉬움) ---");
        String tableWidePlan = explainTableWideNameBrandCount();
        System.out.println(tableWidePlan);
        printPlanDiagnosis("NAME_BRAND table-wide", tableWidePlan);
        System.out.println();
        System.out.println("--- EXPLAIN NAME_BRAND (enable_seqscan=off, category scoped — 인덱스 강제 데모) ---");
        String forcedIndexPlan = explainCountPlan(PublicProductKeywordSearchStrategy.NAME_BRAND, true);
        System.out.println(forcedIndexPlan);
        printPlanDiagnosis("NAME_BRAND forced index", forcedIndexPlan);
        System.out.println();
        printIndexAndSelectivityDiagnostics();
        System.out.println();
        System.out.println("--- EXPLAIN NAME_BRAND combined (isolated, enable_seqscan=off) ---");
        String isolatedCombinedPlan = explainIsolatedNameBrandCombined();
        System.out.println(isolatedCombinedPlan);
        printPlanDiagnosis("NAME_BRAND combined isolated", isolatedCombinedPlan);
        System.out.println();
        System.out.println("--- EXPLAIN rare keyword (1 row, enable_seqscan=off) ---");
        String rarePlan = explainRareKeywordCount();
        System.out.println(rarePlan);
        printPlanDiagnosis("rare keyword", rarePlan);

        Page<PublicProductListProjection> legacyPage = publicProductSearchQueryRepository.search(
                condition,
                PublicProductSort.NEWEST,
                pageable,
                PublicProductKeywordSearchStrategy.NAME_DESCRIPTION_LEGACY
        );
        Page<PublicProductListProjection> nameBrandPage = publicProductSearchQueryRepository.search(
                condition,
                PublicProductSort.NEWEST,
                pageable,
                PublicProductKeywordSearchStrategy.NAME_BRAND
        );

        assertThat(legacyPage.getTotalElements()).isGreaterThan(nameBrandPage.getTotalElements());
        assertThat(nameBrandPage.getTotalElements()).isPositive();
    }

    @Test
    @DisplayName("선택도 구간별 시나리오 비교 표 → Markdown/CSV 파일 출력")
    void exportSelectivityComparisonReport() throws Exception {
        long activeTotal = countActiveProducts();
        List<PublicProductKeywordSelectivityReport.Row> rows = new ArrayList<>();

        for (SelectivityTier tier : SELECTIVITY_TIERS) {
            String pattern = PublicProductKeywordPredicates.likePattern(tier.keyword());
            long matchCount = countNameBrandMatches(pattern);
            double selectivity = 100.0 * matchCount / activeTotal;

            ExplainSnapshot defaultPlan = parseExplain(
                    explainNameBrandCountForKeyword(tier.keyword(), ExplainPlanMode.CATEGORY_SCOPED, categoryId));
            ExplainSnapshot isolatedDefault = parseExplain(
                    explainNameBrandCountForKeyword(tier.keyword(), ExplainPlanMode.ISOLATED_DEFAULT, null));
            ExplainSnapshot ginForced = parseExplain(
                    explainNameBrandCountForKeyword(tier.keyword(), ExplainPlanMode.ISOLATED_GIN_FORCED, null));
            ExplainSnapshot seqScanForced = parseExplain(
                    explainNameBrandCountForKeyword(tier.keyword(), ExplainPlanMode.ISOLATED_SEQ_SCAN_FORCED, null));

            rows.add(new PublicProductKeywordSelectivityReport.Row(
                    tier.label(),
                    tier.keyword(),
                    matchCount,
                    activeTotal,
                    selectivity,
                    defaultPlan.plan(),
                    defaultPlan.seqScan(),
                    defaultPlan.usesGin(),
                    defaultPlan.executionMs(),
                    isolatedDefault.plan(),
                    isolatedDefault.seqScan(),
                    isolatedDefault.usesGin(),
                    isolatedDefault.executionMs(),
                    ginForced.plan(),
                    ginForced.seqScan(),
                    ginForced.usesGin(),
                    ginForced.executionMs(),
                    seqScanForced.plan(),
                    seqScanForced.seqScan(),
                    seqScanForced.usesGin(),
                    seqScanForced.executionMs()
            ));
        }

        Path reportDir = resolveReportDirectory();
        var report = new PublicProductKeywordSelectivityReport(
                (int) activeTotal,
                Instant.now(),
                rows
        );
        report.writeTo(reportDir);

        Path md = reportDir.resolve("keyword-selectivity-comparison.md");
        Path csv = reportDir.resolve("keyword-selectivity-comparison.csv");
        System.out.println("=== Selectivity comparison report written ===");
        System.out.println("  " + md.toAbsolutePath());
        System.out.println("  " + csv.toAbsolutePath());
        System.out.println();
        report.toMarkdown().lines().limit(25).forEach(System.out::println);

        assertThat(md).exists();
        assertThat(csv).exists();
        assertThat(rows).hasSize(SELECTIVITY_TIERS.size());
    }

    private static String resolveBenchmarkProductName(int i) {
        if (i % 997 == 0) {
            return "벤치 kwt0p1 " + i;
        }
        if (i % 101 == 0) {
            return "벤치 kwt1 " + i;
        }
        if (i % 53 == 0) {
            return "벤치 kwt2 " + i;
        }
        if (i % 23 == 0) {
            return "벤치 kwt5 " + i;
        }
        if (i % 11 == 0) {
            return "벤치 kwt9 " + i;
        }
        if (i % 17 == 0) {
            return "와이드핏 데님 " + i;
        }
        return "일반 상품 " + i;
    }

    private static Path resolveReportDirectory() {
        String dir = System.getenv("BENCHMARK_REPORT_DIR");
        if (dir == null || dir.isBlank()) {
            return Paths.get("../../benchmark-reports");
        }
        return Paths.get(dir);
    }

    private long countActiveProducts() {
        Number total = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM products WHERE status = 'ACTIVE'"
        ).getSingleResult();
        return total.longValue();
    }

    private long countNameBrandMatches(String pattern) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM products p
                WHERE p.status = 'ACTIVE'
                  AND %s
                """.formatted(PublicProductKeywordPredicates.nameBrandSearchLikePredicate("p")))
                .setParameter("pattern", pattern)
                .getSingleResult();
        return count.longValue();
    }

    private String explainNameBrandCountForKeyword(
            String searchKeyword,
            ExplainPlanMode mode,
            Long scopedCategoryId
    ) {
        applyExplainPlanMode(mode);
        try {
            String pattern = PublicProductKeywordPredicates.likePattern(searchKeyword);
            if (mode == ExplainPlanMode.CATEGORY_SCOPED) {
                String sql = """
                        EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
                        SELECT count(p.id)
                        FROM products p
                        INNER JOIN categories c ON p.category_id = c.id
                        WHERE p.status = 'ACTIVE'
                          AND c.status = 'ACTIVE'
                          AND c.id = :categoryId
                          AND %s
                        """.formatted(PublicProductKeywordPredicates.nameBrandSearchLikePredicate("p"));
                return runExplain(sql, pattern, scopedCategoryId);
            }
            String sql = """
                    EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
                    SELECT count(p.id) FROM products p
                    WHERE %s
                    """.formatted(PublicProductKeywordPredicates.nameBrandSearchLikePredicate("p"));
            return runExplain(sql, pattern, null);
        } finally {
            resetExplainPlannerSettings();
        }
    }

    private void applyExplainPlanMode(ExplainPlanMode mode) {
        switch (mode) {
            case ISOLATED_GIN_FORCED -> entityManager.createNativeQuery("SET LOCAL enable_seqscan = off")
                    .executeUpdate();
            case ISOLATED_SEQ_SCAN_FORCED -> {
                entityManager.createNativeQuery("SET LOCAL enable_bitmapscan = off").executeUpdate();
                entityManager.createNativeQuery("SET LOCAL enable_indexscan = off").executeUpdate();
                entityManager.createNativeQuery("SET LOCAL enable_seqscan = on").executeUpdate();
            }
            case CATEGORY_SCOPED, ISOLATED_DEFAULT -> { /* planner default */ }
        }
    }

    private void resetExplainPlannerSettings() {
        entityManager.createNativeQuery("SET LOCAL enable_seqscan = on").executeUpdate();
        entityManager.createNativeQuery("SET LOCAL enable_bitmapscan = on").executeUpdate();
        entityManager.createNativeQuery("SET LOCAL enable_indexscan = on").executeUpdate();
    }

    private static ExplainSnapshot parseExplain(String plan) {
        boolean seqScan = plan.contains("Seq Scan on products");
        boolean usesGin = plan.contains("idx_products_name_brand_search_trgm");
        double executionMs = -1;
        int idx = plan.lastIndexOf("Execution Time:");
        if (idx >= 0) {
            String tail = plan.substring(idx + "Execution Time:".length()).trim();
            int end = 0;
            while (end < tail.length() && (Character.isDigit(tail.charAt(end)) || tail.charAt(end) == '.')) {
                end++;
            }
            if (end > 0) {
                executionMs = Double.parseDouble(tail.substring(0, end));
            }
        }
        return new ExplainSnapshot(plan, seqScan, usesGin, executionMs);
    }

    private void warmUp(PublicProductSearchCondition condition, org.springframework.data.domain.Pageable pageable) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.NEWEST, pageable,
                    PublicProductKeywordSearchStrategy.NAME_DESCRIPTION_LEGACY);
            publicProductSearchQueryRepository.search(
                    condition, PublicProductSort.NEWEST, pageable,
                    PublicProductKeywordSearchStrategy.NAME_BRAND);
        }
    }

    private long measureMillis(Runnable search) {
        long total = 0L;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            long start = System.nanoTime();
            search.run();
            total += (System.nanoTime() - start) / 1_000_000L;
        }
        return total / MEASURE_ITERATIONS;
    }

    private static int resolveSeedCount() {
        String raw = System.getenv("BENCHMARK_PRODUCT_COUNT");
        if (raw == null || raw.isBlank()) {
            return 2_000;
//            return 50_000;
        }
        return Integer.parseInt(raw.trim());
    }

    private String explainCountPlan(PublicProductKeywordSearchStrategy strategy, boolean forceIndex) {
        if (forceIndex) {
            entityManager.createNativeQuery("SET LOCAL enable_seqscan = off").executeUpdate();
        }
        try {
            String pattern = PublicProductKeywordPredicates.likePattern(keyword);
            String keywordPredicate = switch (strategy) {
                case NAME_BRAND -> PublicProductKeywordPredicates.nameBrandSearchLikePredicate("p");
                case NAME_DESCRIPTION_LEGACY -> "(lower(p.name) LIKE :pattern OR lower(p.description) LIKE :pattern)";
            };

            String sql = """
                    EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
                    SELECT count(p.id)
                    FROM products p
                    INNER JOIN categories c ON p.category_id = c.id
                    WHERE p.status = 'ACTIVE'
                      AND c.status = 'ACTIVE'
                      AND c.id = :categoryId
                      AND %s
                    """.formatted(keywordPredicate);

            return runExplain(sql, pattern, categoryId);
        } finally {
            if (forceIndex) {
                entityManager.createNativeQuery("SET LOCAL enable_seqscan = on").executeUpdate();
            }
        }
    }

    private String explainTableWideNameBrandCount() {
        String pattern = PublicProductKeywordPredicates.likePattern(keyword);
        String sql = """
                EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
                SELECT count(p.id)
                FROM products p
                WHERE p.status = 'ACTIVE'
                  AND %s
                """.formatted(PublicProductKeywordPredicates.nameBrandSearchLikePredicate("p"));
        return runExplain(sql, pattern, null);
    }

    /**
     * GIN(trgm) 인덱스 존재·크기 확인. 인덱스 "순서"가 아니라 플래너가 선택하는지가 핵심.
     */
    private void printIndexAndSelectivityDiagnostics() {
        @SuppressWarnings("unchecked")
        List<Object[]> indexRows = entityManager.createNativeQuery("""
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE tablename = 'products'
                  AND indexname IN (
                    'idx_products_name_brand_search_trgm',
                    'idx_products_status',
                    'idx_products_category_status'
                  )
                ORDER BY indexname
                """).getResultList();

        System.out.println("--- 인덱스 정의 (pg_indexes) ---");
        for (Object[] row : indexRows) {
            System.out.println("  " + row[0] + ": " + row[1]);
        }

        String pattern = PublicProductKeywordPredicates.likePattern(keyword);
        Number keywordMatches = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM products p
                WHERE p.status = 'ACTIVE'
                  AND %s
                """.formatted(PublicProductKeywordPredicates.nameBrandSearchLikePredicate("p")))
                .setParameter("pattern", pattern).getSingleResult();
        Number activeTotal = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM products WHERE status = 'ACTIVE'"
        ).getSingleResult();

        double pct = 100.0 * keywordMatches.longValue() / activeTotal.longValue();
        System.out.println("--- 키워드 선택도 (시드 설계) ---");
        System.out.printf(
                "  keyword=%s → ACTIVE 중 매칭 %d / %d (%.1f%%)%n",
                keyword, keywordMatches.longValue(), activeTotal.longValue(), pct
        );
        System.out.println("  ~5%% 이상이면 GIN 대신 Seq Scan이 정상에 가깝습니다 (비용 모델).");
        System.out.println("  벤치 시드는 i%%17·i%%31로 의도적으로 ~9%% 매칭입니다.");
        System.out.println("  enable_seqscan=off 시 전 행 ACTIVE면 idx_products_status가 GIN보다 먼저 선택됩니다.");
    }

    private String explainIsolatedNameBrandCombined() {
        return explainNameBrandCountForKeyword(keyword, ExplainPlanMode.ISOLATED_GIN_FORCED, null);
    }

    /** GIN이 보이기 쉬운 극저선택도 패턴 (시드 1건만 매칭). */
    private String explainRareKeywordCount() {
        return explainNameBrandCountForKeyword("xqzbenchuniq", ExplainPlanMode.ISOLATED_GIN_FORCED, null);
    }

    private String runExplain(String sql, String pattern, Long categoryIdParam) {
        var query = entityManager.createNativeQuery(sql).setParameter("pattern", pattern);
        if (categoryIdParam != null) {
            query.setParameter("categoryId", categoryIdParam);
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        return rows.stream()
                .map(PublicProductKeywordSearchBenchmarkIT::explainRowToLine)
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("");
    }

    private static void printPlanDiagnosis(String label, String plan) {
        boolean seqScan = plan.contains("Seq Scan on products");
        boolean ginCombined = plan.contains("idx_products_name_brand_search_trgm");

        System.out.println("[진단: " + label + "]");
        System.out.println("  Seq Scan on products: " + seqScan + (seqScan
                ? " → GIN 미사용(선택도 낮음·status 전체 스캔 등에서 정상)"
                : ""));
        System.out.println("  idx_products_name_brand_search_trgm: " + ginCombined);
        if (plan.contains("Execution Time:")) {
            int idx = plan.lastIndexOf("Execution Time:");
            System.out.println("  " + plan.substring(idx).trim());
        }
    }

    private static String explainRowToLine(Object row) {
        if (row instanceof Object[] cells) {
            return String.join(" ", Arrays.stream(cells)
                    .map(cell -> cell == null ? "" : cell.toString())
                    .toList());
        }
        return row == null ? "" : row.toString();
    }

    private CategoryEntity persistCategory(String name) {
        Instant now = Instant.now();
        return testEntityManager.persistAndFlush(CategoryEntity.builder()
                .name(name + "-" + UUID.randomUUID())
                .status(CategoryStatusEntity.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Configuration
    @Import({ProductDataAccessConfig.class, PublicProductSearchQueryRepository.class})
    @org.springframework.boot.context.properties.EnableConfigurationProperties(PublicProductSearchProperties.class)
    static class Config {
    }
}
