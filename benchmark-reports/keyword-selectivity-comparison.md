# Public product keyword search — selectivity comparison

- Generated: 2026-05-19T22:19:07.854535+09:00
- Seed products (ACTIVE): 50001
- Predicate: `(lower(coalesce(p.name, '')) || ' ' || lower(coalesce(p.brand, ''))) LIKE :pattern`
- GIN index: `idx_products_name_brand_search_trgm`

## Summary (category scoped — 운영 유사)

| 구간 (목표) | 키워드 | 매칭 | 선택도 | 기본 플랜 | GIN | DB ms (기본) |
|-------------|--------|------|--------|-----------|-----|---------------|
|~0.002% (1건)|`xqzbenchuniq`|1 / 50001|0.00%|Bitmap/GIN|yes|2.14|
|~0.1%|`kwt0p1`|51 / 50001|0.10%|Bitmap/GIN|yes|1.39|
|~1%|`kwt1`|495 / 50001|0.99%|Bitmap/GIN|yes|1.37|
|~2%|`kwt2`|934 / 50001|1.87%|Bitmap/GIN|yes|1.89|
|~5%|`kwt5`|2109 / 50001|4.22%|Seq Scan|no|15.87|
|~9%|`kwt9`|4218 / 50001|8.44%|Seq Scan|no|17.54|
|~9% (데님·기존 시드)|`데님`|4014 / 50001|8.03%|Seq Scan|no|18.60|

## Isolated path: planner default vs GIN forced vs Seq Scan forced

동일 조건(키워드만, status/category 없음). 낮은 선택도에서 GIN vs Seq Scan 체감 비교용.

| 구간 | 키워드 | 선택도 | 격리·플래너 기본 | ms (격리·기본) | ms (GIN 강제) | ms (Seq 강제) | Seq÷GIN | GIN 강제 | Seq 강제 |
|------|--------|--------|------------------|----------------|---------------|---------------|---------|----------|----------|
|~0.002% (1건)|`xqzbenchuniq`|0.00%|Bitmap/GIN|1.89|2.07|17.14|8.27x|yes|yes|
|~0.1%|`kwt0p1`|0.10%|Bitmap/GIN|1.30|1.43|16.49|11.57x|yes|yes|
|~1%|`kwt1`|0.99%|Bitmap/GIN|1.40|1.45|16.18|11.18x|yes|yes|
|~2%|`kwt2`|1.87%|Bitmap/GIN|1.84|1.86|15.63|8.43x|yes|yes|
|~5%|`kwt5`|4.22%|Seq Scan|15.19|2.59|15.20|5.87x|yes|yes|
|~9%|`kwt9`|8.44%|Seq Scan|17.23|4.46|17.32|3.88x|yes|yes|
|~9% (데님·기존 시드)|`데님`|8.03%|Seq Scan|17.29|70.84|17.51|0.25x|yes|yes|

**Seq÷GIN** &gt; 1 이면 Seq Scan 강제가 GIN 강제보다 느림 (GIN 유리).

### 강제 설정

- **GIN 강제**: `SET LOCAL enable_seqscan = off`
- **Seq Scan 강제**: `SET LOCAL enable_bitmapscan = off`, `enable_indexscan = off`, `enable_seqscan = on`
- **격리·플래너 기본**: 위 설정 없음 (키워드 조건만)

## Notes

- **기본 플랜**: `status = ACTIVE` + category scope count EXPLAIN.
- 선택도 ~5%% 이상에서 기본(카테고리)은 Seq Scan이 흔함. 격리·GIN 강제 ms는 훨씬 낮을 수 있음.
- 낮은 선택도에서도 Seq 강제 ms를 보면 GIN 대비 얼마나 손해인지 확인 가능.

## Per-scenario detail

### ~0.002% (1건) — `xqzbenchuniq`

**Default (category scoped)**

```
Aggregate  (cost=1372.62..1372.63 rows=1 width=8) (actual time=2.116..2.116 rows=1.00 loops=1)
  Buffers: shared hit=310
  ->  Nested Loop  (cost=1345.12..1372.60 rows=5 width=16) (actual time=2.112..2.113 rows=1.00 loops=1)
        Buffers: shared hit=310
        ->  Index Scan using categories_pkey on categories c  (cost=0.15..8.17 rows=1 width=8) (actual time=0.006..0.007 rows=1.00 loops=1)
              Index Cond: (id = '10'::bigint)
              Filter: (status = 'ACTIVE'::category_status)
              Index Searches: 1
              Buffers: shared hit=2
        ->  Bitmap Heap Scan on products p  (cost=1344.98..1364.39 rows=5 width=24) (actual time=2.105..2.105 rows=1.00 loops=1)
              Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%xqzbenchuniq%'::text)
              Filter: ((category_id = '10'::bigint) AND (status = 'ACTIVE'::product_status))
              Heap Blocks: exact=2
              Buffers: shared hit=308
              ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1344.98 rows=5 width=0) (actual time=2.093..2.093 rows=2.00 loops=1)
                    Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%xqzbenchuniq%'::text)
                    Index Searches: 1
                    Buffers: shared hit=306
Planning:
  Buffers: shared hit=7
Planning Time: 0.132 ms
Execution Time: 2.141 ms
```

**Isolated — planner default**

```
Aggregate  (cost=1364.37..1364.38 rows=1 width=8) (actual time=1.878..1.879 rows=1.00 loops=1)
  Buffers: shared hit=308
  ->  Bitmap Heap Scan on products p  (cost=1344.98..1364.36 rows=5 width=16) (actual time=1.877..1.877 rows=1.00 loops=1)
        Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%xqzbenchuniq%'::text)
        Heap Blocks: exact=2
        Buffers: shared hit=308
        ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1344.98 rows=5 width=0) (actual time=1.870..1.870 rows=2.00 loops=1)
              Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%xqzbenchuniq%'::text)
              Index Searches: 1
              Buffers: shared hit=306
Planning:
  Buffers: shared hit=1
Planning Time: 0.092 ms
Execution Time: 1.888 ms
```

**Isolated — GIN forced**

```
Aggregate  (cost=1364.37..1364.38 rows=1 width=8) (actual time=2.062..2.062 rows=1.00 loops=1)
  Buffers: shared hit=308
  ->  Bitmap Heap Scan on products p  (cost=1344.98..1364.36 rows=5 width=16) (actual time=2.060..2.060 rows=1.00 loops=1)
        Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%xqzbenchuniq%'::text)
        Heap Blocks: exact=2
        Buffers: shared hit=308
        ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1344.98 rows=5 width=0) (actual time=2.047..2.047 rows=2.00 loops=1)
              Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%xqzbenchuniq%'::text)
              Index Searches: 1
              Buffers: shared hit=306
Planning:
  Buffers: shared hit=1
Planning Time: 0.052 ms
Execution Time: 2.072 ms
```

**Isolated — Seq Scan forced**

```
Aggregate  (cost=3254.03..3254.05 rows=1 width=8) (actual time=17.126..17.126 rows=1.00 loops=1)
  Buffers: shared hit=2129
  ->  Seq Scan on products p  (cost=0.00..3254.02 rows=5 width=16) (actual time=17.121..17.122 rows=1.00 loops=1)
        Filter: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%xqzbenchuniq%'::text)
        Rows Removed by Filter: 50000
        Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.064 ms
Execution Time: 17.138 ms
```

### ~0.1% — `kwt0p1`

**Default (category scoped)**

```
Aggregate  (cost=1294.87..1294.88 rows=1 width=8) (actual time=1.364..1.364 rows=1.00 loops=1)
  Buffers: shared hit=404
  ->  Nested Loop  (cost=1267.38..1294.86 rows=5 width=16) (actual time=1.321..1.360 rows=51.00 loops=1)
        Buffers: shared hit=404
        ->  Index Scan using categories_pkey on categories c  (cost=0.15..8.17 rows=1 width=8) (actual time=0.009..0.010 rows=1.00 loops=1)
              Index Cond: (id = '10'::bigint)
              Filter: (status = 'ACTIVE'::category_status)
              Index Searches: 1
              Buffers: shared hit=2
        ->  Bitmap Heap Scan on products p  (cost=1267.23..1286.64 rows=5 width=24) (actual time=1.311..1.347 rows=51.00 loops=1)
              Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt0p1%'::text)
              Filter: ((category_id = '10'::bigint) AND (status = 'ACTIVE'::product_status))
              Heap Blocks: exact=102
              Buffers: shared hit=402
              ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1267.23 rows=5 width=0) (actual time=1.287..1.287 rows=102.00 loops=1)
                    Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt0p1%'::text)
                    Index Searches: 1
                    Buffers: shared hit=300
Planning:
  Buffers: shared hit=1
Planning Time: 0.087 ms
Execution Time: 1.392 ms
```

**Isolated — planner default**

```
Aggregate  (cost=1286.63..1286.64 rows=1 width=8) (actual time=1.291..1.291 rows=1.00 loops=1)
  Buffers: shared hit=402
  ->  Bitmap Heap Scan on products p  (cost=1267.23..1286.62 rows=5 width=16) (actual time=1.254..1.288 rows=51.00 loops=1)
        Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt0p1%'::text)
        Heap Blocks: exact=102
        Buffers: shared hit=402
        ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1267.23 rows=5 width=0) (actual time=1.231..1.231 rows=102.00 loops=1)
              Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt0p1%'::text)
              Index Searches: 1
              Buffers: shared hit=300
Planning:
  Buffers: shared hit=1
Planning Time: 0.060 ms
Execution Time: 1.303 ms
```

**Isolated — GIN forced**

```
Aggregate  (cost=1286.63..1286.64 rows=1 width=8) (actual time=1.409..1.409 rows=1.00 loops=1)
  Buffers: shared hit=402
  ->  Bitmap Heap Scan on products p  (cost=1267.23..1286.62 rows=5 width=16) (actual time=1.368..1.405 rows=51.00 loops=1)
        Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt0p1%'::text)
        Heap Blocks: exact=102
        Buffers: shared hit=402
        ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1267.23 rows=5 width=0) (actual time=1.336..1.336 rows=102.00 loops=1)
              Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt0p1%'::text)
              Index Searches: 1
              Buffers: shared hit=300
Planning:
  Buffers: shared hit=1
Planning Time: 0.054 ms
Execution Time: 1.425 ms
```

**Isolated — Seq Scan forced**

```
Aggregate  (cost=3254.03..3254.05 rows=1 width=8) (actual time=16.475..16.475 rows=1.00 loops=1)
  Buffers: shared hit=2129
  ->  Seq Scan on products p  (cost=0.00..3254.02 rows=5 width=16) (actual time=1.005..16.467 rows=51.00 loops=1)
        Filter: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt0p1%'::text)
        Rows Removed by Filter: 49950
        Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.049 ms
Execution Time: 16.486 ms
```

### ~1% — `kwt1`

**Default (category scoped)**

```
Aggregate  (cost=2455.51..2455.52 rows=1 width=8) (actual time=1.348..1.349 rows=1.00 loops=1)
  Buffers: shared hit=1287
  ->  Nested Loop  (cost=1244.09..2454.25 rows=505 width=16) (actual time=1.038..1.328 rows=495.00 loops=1)
        Buffers: shared hit=1287
        ->  Index Scan using categories_pkey on categories c  (cost=0.15..8.17 rows=1 width=8) (actual time=0.009..0.009 rows=1.00 loops=1)
              Index Cond: (id = '10'::bigint)
              Filter: (status = 'ACTIVE'::category_status)
              Index Searches: 1
              Buffers: shared hit=2
        ->  Bitmap Heap Scan on products p  (cost=1243.94..2441.03 rows=505 width=24) (actual time=1.029..1.293 rows=495.00 loops=1)
              Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt1%'::text)
              Filter: ((category_id = '10'::bigint) AND (status = 'ACTIVE'::product_status))
              Heap Blocks: exact=989
              Buffers: shared hit=1285
              ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1243.82 rows=505 width=0) (actual time=0.884..0.884 rows=990.00 loops=1)
                    Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt1%'::text)
                    Index Searches: 1
                    Buffers: shared hit=296
Planning:
  Buffers: shared hit=1
Planning Time: 0.087 ms
Execution Time: 1.366 ms
```

**Isolated — planner default**

```
Aggregate  (cost=2439.77..2439.78 rows=1 width=8) (actual time=1.384..1.384 rows=1.00 loops=1)
  Buffers: shared hit=1285
  ->  Bitmap Heap Scan on products p  (cost=1243.94..2438.50 rows=505 width=16) (actual time=1.104..1.363 rows=495.00 loops=1)
        Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt1%'::text)
        Heap Blocks: exact=989
        Buffers: shared hit=1285
        ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1243.82 rows=505 width=0) (actual time=0.934..0.934 rows=990.00 loops=1)
              Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt1%'::text)
              Index Searches: 1
              Buffers: shared hit=296
Planning:
  Buffers: shared hit=1
Planning Time: 0.035 ms
Execution Time: 1.395 ms
```

**Isolated — GIN forced**

```
Aggregate  (cost=2439.77..2439.78 rows=1 width=8) (actual time=1.436..1.436 rows=1.00 loops=1)
  Buffers: shared hit=1285
  ->  Bitmap Heap Scan on products p  (cost=1243.94..2438.50 rows=505 width=16) (actual time=1.128..1.415 rows=495.00 loops=1)
        Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt1%'::text)
        Heap Blocks: exact=989
        Buffers: shared hit=1285
        ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1243.82 rows=505 width=0) (actual time=0.952..0.952 rows=990.00 loops=1)
              Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt1%'::text)
              Index Searches: 1
              Buffers: shared hit=296
Planning:
  Buffers: shared hit=1
Planning Time: 0.043 ms
Execution Time: 1.447 ms
```

**Isolated — Seq Scan forced**

```
Aggregate  (cost=3255.28..3255.30 rows=1 width=8) (actual time=16.168..16.168 rows=1.00 loops=1)
  Buffers: shared hit=2129
  ->  Seq Scan on products p  (cost=0.00..3254.02 rows=505 width=16) (actual time=0.858..16.145 rows=495.00 loops=1)
        Filter: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt1%'::text)
        Rows Removed by Filter: 49506
        Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.049 ms
Execution Time: 16.177 ms
```

### ~2% — `kwt2`

**Default (category scoped)**

```
Aggregate  (cost=2455.51..2455.52 rows=1 width=8) (actual time=1.865..1.865 rows=1.00 loops=1)
  Buffers: shared hit=2163
  ->  Nested Loop  (cost=1244.09..2454.25 rows=505 width=16) (actual time=1.321..1.830 rows=934.00 loops=1)
        Buffers: shared hit=2163
        ->  Index Scan using categories_pkey on categories c  (cost=0.15..8.17 rows=1 width=8) (actual time=0.007..0.008 rows=1.00 loops=1)
              Index Cond: (id = '10'::bigint)
              Filter: (status = 'ACTIVE'::category_status)
              Index Searches: 1
              Buffers: shared hit=2
        ->  Bitmap Heap Scan on products p  (cost=1243.94..2441.03 rows=505 width=24) (actual time=1.313..1.780 rows=934.00 loops=1)
              Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt2%'::text)
              Filter: ((category_id = '10'::bigint) AND (status = 'ACTIVE'::product_status))
              Heap Blocks: exact=1865
              Buffers: shared hit=2161
              ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1243.82 rows=505 width=0) (actual time=1.009..1.009 rows=1868.00 loops=1)
                    Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt2%'::text)
                    Index Searches: 1
                    Buffers: shared hit=296
Planning:
  Buffers: shared hit=1
Planning Time: 0.081 ms
Execution Time: 1.893 ms
```

**Isolated — planner default**

```
Aggregate  (cost=2439.77..2439.78 rows=1 width=8) (actual time=1.821..1.821 rows=1.00 loops=1)
  Buffers: shared hit=2161
  ->  Bitmap Heap Scan on products p  (cost=1243.94..2438.50 rows=505 width=16) (actual time=1.322..1.786 rows=934.00 loops=1)
        Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt2%'::text)
        Heap Blocks: exact=1865
        Buffers: shared hit=2161
        ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1243.82 rows=505 width=0) (actual time=1.009..1.009 rows=1868.00 loops=1)
              Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt2%'::text)
              Index Searches: 1
              Buffers: shared hit=296
Planning:
  Buffers: shared hit=1
Planning Time: 0.044 ms
Execution Time: 1.835 ms
```

**Isolated — GIN forced**

```
Aggregate  (cost=2439.77..2439.78 rows=1 width=8) (actual time=1.843..1.843 rows=1.00 loops=1)
  Buffers: shared hit=2161
  ->  Bitmap Heap Scan on products p  (cost=1243.94..2438.50 rows=505 width=16) (actual time=1.319..1.807 rows=934.00 loops=1)
        Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt2%'::text)
        Heap Blocks: exact=1865
        Buffers: shared hit=2161
        ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1243.82 rows=505 width=0) (actual time=1.026..1.026 rows=1868.00 loops=1)
              Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt2%'::text)
              Index Searches: 1
              Buffers: shared hit=296
Planning:
  Buffers: shared hit=1
Planning Time: 0.029 ms
Execution Time: 1.855 ms
```

**Isolated — Seq Scan forced**

```
Aggregate  (cost=3255.28..3255.30 rows=1 width=8) (actual time=15.624..15.625 rows=1.00 loops=1)
  Buffers: shared hit=2129
  ->  Seq Scan on products p  (cost=0.00..3254.02 rows=505 width=16) (actual time=0.730..15.585 rows=934.00 loops=1)
        Filter: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt2%'::text)
        Rows Removed by Filter: 49067
        Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.033 ms
Execution Time: 15.630 ms
```

### ~5% — `kwt5`

**Default (category scoped)**

```
Aggregate  (cost=3543.76..3543.77 rows=1 width=8) (actual time=15.833..15.834 rows=1.00 loops=1)
  Buffers: shared hit=2131
  ->  Nested Loop  (cost=0.15..3537.45 rows=2525 width=16) (actual time=0.561..15.731 rows=2109.00 loops=1)
        Buffers: shared hit=2131
        ->  Index Scan using categories_pkey on categories c  (cost=0.15..8.17 rows=1 width=8) (actual time=0.009..0.011 rows=1.00 loops=1)
              Index Cond: (id = '10'::bigint)
              Filter: (status = 'ACTIVE'::category_status)
              Index Searches: 1
              Buffers: shared hit=2
        ->  Seq Scan on products p  (cost=0.00..3504.03 rows=2525 width=24) (actual time=0.551..15.618 rows=2109.00 loops=1)
              Filter: ((category_id = '10'::bigint) AND (status = 'ACTIVE'::product_status) AND (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt5%'::text))
              Rows Removed by Filter: 47892
              Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.068 ms
Execution Time: 15.869 ms
```

**Isolated — planner default**

```
Aggregate  (cost=3260.34..3260.35 rows=1 width=8) (actual time=15.182..15.183 rows=1.00 loops=1)
  Buffers: shared hit=2129
  ->  Seq Scan on products p  (cost=0.00..3254.02 rows=2525 width=16) (actual time=0.543..15.098 rows=2109.00 loops=1)
        Filter: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt5%'::text)
        Rows Removed by Filter: 47892
        Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.032 ms
Execution Time: 15.188 ms
```

**Isolated — GIN forced**

```
Aggregate  (cost=3555.02..3555.03 rows=1 width=8) (actual time=2.569..2.570 rows=1.00 loops=1)
  Buffers: shared hit=2416
  ->  Bitmap Heap Scan on products p  (cost=1254.55..3548.71 rows=2525 width=16) (actual time=1.617..2.496 rows=2109.00 loops=1)
        Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt5%'::text)
        Heap Blocks: exact=2119
        Buffers: shared hit=2416
        ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1253.92 rows=2525 width=0) (actual time=1.214..1.214 rows=4218.00 loops=1)
              Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt5%'::text)
              Index Searches: 1
              Buffers: shared hit=297
Planning:
  Buffers: shared hit=1
Planning Time: 0.050 ms
Execution Time: 2.587 ms
```

**Isolated — Seq Scan forced**

```
Aggregate  (cost=3260.34..3260.35 rows=1 width=8) (actual time=15.191..15.191 rows=1.00 loops=1)
  Buffers: shared hit=2129
  ->  Seq Scan on products p  (cost=0.00..3254.02 rows=2525 width=16) (actual time=0.557..15.110 rows=2109.00 loops=1)
        Filter: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt5%'::text)
        Rows Removed by Filter: 47892
        Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.032 ms
Execution Time: 15.196 ms
```

### ~9% — `kwt9`

**Default (category scoped)**

```
Aggregate  (cost=3562.70..3562.71 rows=1 width=8) (actual time=17.477..17.478 rows=1.00 loops=1)
  Buffers: shared hit=2131
  ->  Nested Loop  (cost=0.15..3552.60 rows=4040 width=16) (actual time=0.838..17.306 rows=4218.00 loops=1)
        Buffers: shared hit=2131
        ->  Index Scan using categories_pkey on categories c  (cost=0.15..8.17 rows=1 width=8) (actual time=0.013..0.016 rows=1.00 loops=1)
              Index Cond: (id = '10'::bigint)
              Filter: (status = 'ACTIVE'::category_status)
              Index Searches: 1
              Buffers: shared hit=2
        ->  Seq Scan on products p  (cost=0.00..3504.03 rows=4040 width=24) (actual time=0.824..17.090 rows=4218.00 loops=1)
              Filter: ((category_id = '10'::bigint) AND (status = 'ACTIVE'::product_status) AND (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt9%'::text))
              Rows Removed by Filter: 45783
              Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.100 ms
Execution Time: 17.542 ms
```

**Isolated — planner default**

```
Aggregate  (cost=3264.12..3264.13 rows=1 width=8) (actual time=17.217..17.218 rows=1.00 loops=1)
  Buffers: shared hit=2129
  ->  Seq Scan on products p  (cost=0.00..3254.02 rows=4040 width=16) (actual time=0.920..17.037 rows=4218.00 loops=1)
        Filter: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt9%'::text)
        Rows Removed by Filter: 45783
        Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.067 ms
Execution Time: 17.230 ms
```

**Isolated — GIN forced**

```
Aggregate  (cost=3522.52..3522.53 rows=1 width=8) (actual time=4.423..4.424 rows=1.00 loops=1)
  Buffers: shared hit=2422
  ->  Bitmap Heap Scan on products p  (cost=1266.63..3512.42 rows=4040 width=16) (actual time=2.146..4.276 rows=4218.00 loops=1)
        Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt9%'::text)
        Heap Blocks: exact=2123
        Buffers: shared hit=2422
        ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..1265.62 rows=4040 width=0) (actual time=1.459..1.459 rows=8436.00 loops=1)
              Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt9%'::text)
              Index Searches: 1
              Buffers: shared hit=299
Planning:
  Buffers: shared hit=1
Planning Time: 0.066 ms
Execution Time: 4.459 ms
```

**Isolated — Seq Scan forced**

```
Aggregate  (cost=3264.12..3264.13 rows=1 width=8) (actual time=17.306..17.306 rows=1.00 loops=1)
  Buffers: shared hit=2129
  ->  Seq Scan on products p  (cost=0.00..3254.02 rows=4040 width=16) (actual time=1.013..17.134 rows=4218.00 loops=1)
        Filter: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%kwt9%'::text)
        Rows Removed by Filter: 45783
        Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.070 ms
Execution Time: 17.320 ms
```

### ~9% (데님·기존 시드) — `데님`

**Default (category scoped)**

```
Aggregate  (cost=3562.70..3562.71 rows=1 width=8) (actual time=18.526..18.527 rows=1.00 loops=1)
  Buffers: shared hit=2131
  ->  Nested Loop  (cost=0.15..3552.60 rows=4040 width=16) (actual time=0.216..18.357 rows=4014.00 loops=1)
        Buffers: shared hit=2131
        ->  Index Scan using categories_pkey on categories c  (cost=0.15..8.17 rows=1 width=8) (actual time=0.013..0.016 rows=1.00 loops=1)
              Index Cond: (id = '10'::bigint)
              Filter: (status = 'ACTIVE'::category_status)
              Index Searches: 1
              Buffers: shared hit=2
        ->  Seq Scan on products p  (cost=0.00..3504.03 rows=4040 width=24) (actual time=0.202..18.141 rows=4014.00 loops=1)
              Filter: ((category_id = '10'::bigint) AND (status = 'ACTIVE'::product_status) AND (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%데님%'::text))
              Rows Removed by Filter: 45987
              Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.119 ms
Execution Time: 18.604 ms
```

**Isolated — planner default**

```
Aggregate  (cost=3264.12..3264.13 rows=1 width=8) (actual time=17.273..17.273 rows=1.00 loops=1)
  Buffers: shared hit=2129
  ->  Seq Scan on products p  (cost=0.00..3254.02 rows=4040 width=16) (actual time=0.192..17.112 rows=4014.00 loops=1)
        Filter: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%데님%'::text)
        Rows Removed by Filter: 45987
        Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.063 ms
Execution Time: 17.285 ms
```

**Isolated — GIN forced**

```
Aggregate  (cost=820099.61..820099.62 rows=1 width=8) (actual time=70.301..70.302 rows=1.00 loops=1)
  Buffers: shared hit=2885
  ->  Bitmap Heap Scan on products p  (cost=817843.71..820089.51 rows=4040 width=16) (actual time=50.717..70.169 rows=4014.00 loops=1)
        Recheck Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%데님%'::text)
        Rows Removed by Index Recheck: 45987
        Heap Blocks: exact=2129
        Buffers: shared hit=2885
        ->  Bitmap Index Scan on idx_products_name_brand_search_trgm  (cost=0.00..817842.70 rows=4040 width=0) (actual time=11.862..11.862 rows=100207.00 loops=1)
              Index Cond: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%데님%'::text)
              Index Searches: 1
              Buffers: shared hit=756
Planning:
  Buffers: shared hit=1
Planning Time: 0.063 ms
JIT:
  Functions: 5
  Options: Inlining true, Optimization true, Expressions true, Deforming true
  Timing: Generation 0.434 ms (Deform 0.142 ms), Inlining 7.292 ms, Optimization 17.793 ms, Emission 13.345 ms, Total 38.864 ms
Execution Time: 70.838 ms
```

**Isolated — Seq Scan forced**

```
Aggregate  (cost=3264.12..3264.13 rows=1 width=8) (actual time=17.496..17.497 rows=1.00 loops=1)
  Buffers: shared hit=2129
  ->  Seq Scan on products p  (cost=0.00..3254.02 rows=4040 width=16) (actual time=0.211..17.328 rows=4014.00 loops=1)
        Filter: (((lower((COALESCE(name, ''::character varying))::text) || ' '::text) || lower((COALESCE(brand, ''::character varying))::text)) ~~ '%데님%'::text)
        Rows Removed by Filter: 45987
        Buffers: shared hit=2129
Planning:
  Buffers: shared hit=1
Planning Time: 0.080 ms
Execution Time: 17.511 ms
```

