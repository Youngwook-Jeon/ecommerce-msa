package com.project.young.productservice.dataaccess.repository;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 선택도 구간별 벤치마크 결과 (Markdown / CSV).
 */
record PublicProductKeywordSelectivityReport(
        int seedProductCount,
        Instant generatedAt,
        List<Row> rows
) {

    record Row(
            String tierLabel,
            String keyword,
            long matchCount,
            long activeTotal,
            double selectivityPercent,
            String defaultPlan,
            boolean defaultSeqScan,
            boolean defaultUsesGin,
            double defaultExecutionMs,
            String isolatedDefaultPlan,
            boolean isolatedDefaultSeqScan,
            boolean isolatedDefaultUsesGin,
            double isolatedDefaultExecutionMs,
            String ginForcedPlan,
            boolean ginForcedSeqScan,
            boolean ginForcedUsesGin,
            double ginForcedExecutionMs,
            String seqScanForcedPlan,
            boolean seqScanForcedSeqScan,
            boolean seqScanForcedUsesGin,
            double seqScanForcedExecutionMs
    ) {
        double seqOverGinRatio() {
            if (ginForcedExecutionMs <= 0 || seqScanForcedExecutionMs < 0) {
                return -1;
            }
            return seqScanForcedExecutionMs / ginForcedExecutionMs;
        }
    }

    void writeTo(Path directory) throws java.io.IOException {
        java.nio.file.Files.createDirectories(directory);
        Path md = directory.resolve("keyword-selectivity-comparison.md");
        Path csv = directory.resolve("keyword-selectivity-comparison.csv");
        java.nio.file.Files.writeString(md, toMarkdown());
        java.nio.file.Files.writeString(csv, toCsv());
    }

    String toMarkdown() {
        String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withZone(ZoneId.systemDefault())
                .format(generatedAt);

        StringBuilder sb = new StringBuilder();
        sb.append("# Public product keyword search — selectivity comparison\n\n");
        sb.append("- Generated: ").append(timestamp).append("\n");
        sb.append("- Seed products (ACTIVE): ").append(seedProductCount).append("\n");
        sb.append("- Predicate: `").append(PublicProductKeywordPredicates.nameBrandSearchLikePredicate("p"))
                .append("`\n");
        sb.append("- GIN index: `idx_products_name_brand_search_trgm`\n\n");

        sb.append("## Summary (category scoped — 운영 유사)\n\n");
        sb.append("| 구간 (목표) | 키워드 | 매칭 | 선택도 | 기본 플랜 | GIN | DB ms (기본) |\n");
        sb.append("|-------------|--------|------|--------|-----------|-----|---------------|\n");
        for (Row row : rows) {
            sb.append('|').append(row.tierLabel()).append('|');
            sb.append('`').append(row.keyword()).append('`').append('|');
            sb.append(row.matchCount()).append(" / ").append(row.activeTotal()).append('|');
            sb.append(String.format(Locale.US, "%.2f%%", row.selectivityPercent())).append('|');
            sb.append(planShort(row.defaultPlan(), row.defaultSeqScan(), row.defaultUsesGin())).append('|');
            sb.append(row.defaultUsesGin() ? "yes" : "no").append('|');
            sb.append(formatMs(row.defaultExecutionMs())).append("|\n");
        }

        sb.append("\n## Isolated path: planner default vs GIN forced vs Seq Scan forced\n\n");
        sb.append("동일 조건(키워드만, status/category 없음). 낮은 선택도에서 GIN vs Seq Scan 체감 비교용.\n\n");
        sb.append("| 구간 | 키워드 | 선택도 | 격리·플래너 기본 | ms (격리·기본) | ms (GIN 강제) | ms (Seq 강제) | Seq÷GIN | GIN 강제 | Seq 강제 |\n");
        sb.append("|------|--------|--------|------------------|----------------|---------------|---------------|---------|----------|----------|\n");
        for (Row row : rows) {
            sb.append('|').append(row.tierLabel()).append('|');
            sb.append('`').append(row.keyword()).append('`').append('|');
            sb.append(String.format(Locale.US, "%.2f%%", row.selectivityPercent())).append('|');
            sb.append(planShort(row.isolatedDefaultPlan(), row.isolatedDefaultSeqScan(), row.isolatedDefaultUsesGin()))
                    .append('|');
            sb.append(formatMs(row.isolatedDefaultExecutionMs())).append('|');
            sb.append(formatMs(row.ginForcedExecutionMs())).append('|');
            sb.append(formatMs(row.seqScanForcedExecutionMs())).append('|');
            sb.append(formatRatio(row.seqOverGinRatio())).append('|');
            sb.append(row.ginForcedUsesGin() ? "yes" : "no").append('|');
            sb.append(row.seqScanForcedSeqScan() ? "yes" : "no").append("|\n");
        }

        sb.append("\n**Seq÷GIN** &gt; 1 이면 Seq Scan 강제가 GIN 강제보다 느림 (GIN 유리).\n\n");
        sb.append("### 강제 설정\n\n");
        sb.append("- **GIN 강제**: `SET LOCAL enable_seqscan = off`\n");
        sb.append("- **Seq Scan 강제**: `SET LOCAL enable_bitmapscan = off`, `enable_indexscan = off`, `enable_seqscan = on`\n");
        sb.append("- **격리·플래너 기본**: 위 설정 없음 (키워드 조건만)\n\n");

        sb.append("## Notes\n\n");
        sb.append("- **기본 플랜**: `status = ACTIVE` + category scope count EXPLAIN.\n");
        sb.append("- 선택도 ~5%% 이상에서 기본(카테고리)은 Seq Scan이 흔함. 격리·GIN 강제 ms는 훨씬 낮을 수 있음.\n");
        sb.append("- 낮은 선택도에서도 Seq 강제 ms를 보면 GIN 대비 얼마나 손해인지 확인 가능.\n\n");

        sb.append("## Per-scenario detail\n\n");
        for (Row row : rows) {
            sb.append("### ").append(row.tierLabel()).append(" — `").append(row.keyword()).append("`\n\n");
            appendPlanSection(sb, "Default (category scoped)", row.defaultPlan());
            appendPlanSection(sb, "Isolated — planner default", row.isolatedDefaultPlan());
            appendPlanSection(sb, "Isolated — GIN forced", row.ginForcedPlan());
            appendPlanSection(sb, "Isolated — Seq Scan forced", row.seqScanForcedPlan());
        }
        return sb.toString();
    }

    private static void appendPlanSection(StringBuilder sb, String title, String plan) {
        sb.append("**").append(title).append("**\n\n```\n");
        sb.append(plan.trim()).append("\n```\n\n");
    }

    private static String planShort(String plan, boolean seqScan, boolean usesGin) {
        if (usesGin) {
            return "Bitmap/GIN";
        }
        if (seqScan) {
            return "Seq Scan";
        }
        if (plan.contains("idx_products_status")) {
            return "status idx + Filter";
        }
        if (plan.contains("idx_products_category_status")) {
            return "category_status + Filter";
        }
        return "other";
    }

    private static String formatMs(double ms) {
        if (ms < 0) {
            return "n/a";
        }
        return String.format(Locale.US, "%.2f", ms);
    }

    private static String formatRatio(double ratio) {
        if (ratio < 0) {
            return "n/a";
        }
        return String.format(Locale.US, "%.2fx", ratio);
    }

    String toCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("tier_label,keyword,match_count,active_total,selectivity_percent,");
        sb.append("default_plan,default_seq_scan,default_uses_gin,default_execution_ms,");
        sb.append("isolated_default_plan,isolated_default_seq_scan,isolated_default_uses_gin,isolated_default_execution_ms,");
        sb.append("gin_forced_seq_scan,gin_forced_uses_gin,gin_forced_execution_ms,");
        sb.append("seq_scan_forced_seq_scan,seq_scan_forced_uses_gin,seq_scan_forced_execution_ms,seq_over_gin_ratio\n");
        for (Row row : rows) {
            sb.append(csv(row.tierLabel())).append(',');
            sb.append(csv(row.keyword())).append(',');
            sb.append(row.matchCount()).append(',');
            sb.append(row.activeTotal()).append(',');
            sb.append(String.format(Locale.US, "%.4f", row.selectivityPercent())).append(',');
            sb.append(csv(planShort(row.defaultPlan(), row.defaultSeqScan(), row.defaultUsesGin()))).append(',');
            sb.append(row.defaultSeqScan()).append(',');
            sb.append(row.defaultUsesGin()).append(',');
            sb.append(String.format(Locale.US, "%.4f", row.defaultExecutionMs())).append(',');
            sb.append(csv(planShort(row.isolatedDefaultPlan(), row.isolatedDefaultSeqScan(), row.isolatedDefaultUsesGin())))
                    .append(',');
            sb.append(row.isolatedDefaultSeqScan()).append(',');
            sb.append(row.isolatedDefaultUsesGin()).append(',');
            sb.append(String.format(Locale.US, "%.4f", row.isolatedDefaultExecutionMs())).append(',');
            sb.append(row.ginForcedSeqScan()).append(',');
            sb.append(row.ginForcedUsesGin()).append(',');
            sb.append(String.format(Locale.US, "%.4f", row.ginForcedExecutionMs())).append(',');
            sb.append(row.seqScanForcedSeqScan()).append(',');
            sb.append(row.seqScanForcedUsesGin()).append(',');
            sb.append(String.format(Locale.US, "%.4f", row.seqScanForcedExecutionMs())).append(',');
            sb.append(String.format(Locale.US, "%.4f", row.seqOverGinRatio())).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
