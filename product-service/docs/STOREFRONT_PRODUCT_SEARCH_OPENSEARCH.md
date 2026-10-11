# OpenSearch 상품 검색 어댑터

`ProductSearchPort`는 product-dataaccess의 `OpenSearchProductSearchAdapter`가 구현한다. 공식 `opensearch-java:3.9.0`과 Apache HttpClient 5 transport를 사용한다. HTTP DTO나 OpenSearch 타입을 application의 포트에 노출하지 않는다.

## 설정

백엔드 환경 변수는 `ecommerce-msa/.env.example`에 기재한다. 로컬 스크립트가 `.env`를 읽어 서비스 프로세스에 전달하며 실제 `.env`를 자동 수정하지 않는다.

| 변수 | 기본값 | 의미 |
|---|---|---|
| `PRODUCT_SEARCH_ENABLED` | `true` | false이면 클라이언트 없이 기존 503 검색 불가 포트를 등록 |
| `PRODUCT_SEARCH_ENDPOINT` | `http://localhost:9200` | HTTP(S) origin. 경로/인증정보를 넣지 않음 |
| `PRODUCT_SEARCH_INDEX` | `products-read` | 읽기 alias 또는 단일 인덱스 이름 |
| `PRODUCT_SEARCH_CONNECT_TIMEOUT_MILLIS` | `1000` | 연결 제한 |
| `PRODUCT_SEARCH_RESPONSE_TIMEOUT_MILLIS` | `5000` | HTTP 응답 대기 제한 |
| `PRODUCT_SEARCH_QUERY_TIMEOUT_SECONDS` | `3` | 각 검색의 서버 타임아웃 |
| `PRODUCT_SEARCH_REQUEST_BUDGET_MILLIS` | `10000` | PIT 생성부터 집계 반복까지의 시간 예산. 다음 집계 요청 전에 검사하며 진행 중 HTTP 요청/정리 시간만큼 초과 가능 |

Spring 시작 시 네트워크 연결이나 인덱스 자동 생성은 수행하지 않는다. 인덱스 부재, 통신 오류, 검색 타임아웃/부분 shard 실패, 불완전한 집계/문서는 `SEARCH_UNAVAILABLE`로 처리한다. PostgreSQL 검색으로 대체하지 않는다.

## 인덱스와 문서

명시적인 매핑은 [products-index.json](../product-dataaccess/src/main/resources/opensearch/products-index.json)을 사용한다. 로컬 Nori 이미지가 필요하다. 기본 매핑은 primary shards 2개, replicas 0개이며 운영 배치 시 조정한다.

새 클러스터에서 초기 인덱스를 준비하려면 **ecommerce-msa 디렉터리**에서 다음 명령을 실행한다. 이미 존재하는 인덱스를 삭제/재생성하지 않는다. 이미 alias를 사용하는 경우 새 버전의 전환 절차를 별도로 정한다.

```bash
curl --fail -H 'Content-Type: application/json' -X PUT \
  http://localhost:9200/products-v1 \
  --data-binary @product-service/product-dataaccess/src/main/resources/opensearch/products-index.json
curl --fail -X PUT http://localhost:9200/products-v1/_alias/products-read
```

인덱싱 단위는 상품이며 `_id`와 문서 `id`는 상품 UUID를 사용한다. 검색 문서는 `OpenSearchProductDocument`의 필드를 따른다.

- 필수 데이터: id, categoryId, categoryName, name, basePrice, productStatus, categoryStatus, createdAt.
- brand/mainImageUrl은 null 가능. description은 전문 검색에 사용한다.
- brandLabel은 브랜드 표시 이름을 NFC/앞뒤 Unicode 공백 제거한 값이다. brandKey는 해당 값을 `Locale.ROOT` 소문자로 변환하고 다시 NFC 정규화한다. brand는 전문 검색과 상품 응답에 사용하는 표시 이름이다. 빈 브랜드는 세 필드를 null로 두거나 생략한다.
- brandKey는 필터와 정확한 집계에 사용한다. 대소문자만 다른 브랜드는 같은 키로 합산하며, 동일 키의 표시 이름이 여러 개면 사전순 첫 brandLabel을 선택한다.
- basePrice는 소수점 이하 최대 두 자리, 0 이상인 카탈로그 금액이다. 매핑은 scaled_float(100), 집계 응답은 두 자리 BigDecimal로 변환해 부동소수점 오차를 제거한다.
- createdAt은 ISO-8601 시각 문자열이다. 정렬에만 사용하며 공개 상품 응답에는 추가하지 않는다.
- 브랜드 이름 변경 시 관련 상품 문서의 brand/brandLabel/brandKey를 함께 갱신한다. 이름 변경에 따라 필터 키도 바뀔 수 있다.
- 다른 브랜드 필드 매핑으로 생성된 기존 인덱스는 새 매핑으로 다시 색인한 후 alias를 전환해야 한다.
- 상품 생성/수정/삭제 및 카테고리 상태/이름 변경을 문서에 반영하는 DB 초기 적재와 outbox 동기화는 후속 작업이다. 어댑터만으로 DB 상품이 자동 색인되지 않는다.

## 쿼리 구현 교체

`OpenSearchProductQueryBuilder`는 템플릿 메서드의 추상 기반 클래스다. `final build()`가 공통 필터, 정렬, 집계, PIT와 페이지 설정을 담당하며, 하위 클래스는 `buildTextQuery(keyword)`만 구현한다. 검색어가 없으면 해당 메서드를 호출하지 않는다.

기본 구현 `CrossFieldsProductQueryBuilder`는 기존 cross_fields AND 검색과 상품명 구문 가중치를 유지한다. 다른 구현으로 교체하려면 `OpenSearchProductQueryBuilder` 타입의 Spring bean을 등록한다. 기본 bean은 해당 타입의 bean이 없을 때만 등록되며 어댑터는 주입받은 빌더를 사용한다.

`includeProductsAndPrice`는 상품 페이지 번호가 아니라, 이번 OpenSearch 호출에서 상품 목록과 가격 집계를 함께 요청할지 나타낸다. 첫 호출만 true이며 이후 브랜드 집계 호출은 false다.

## 검색과 정확한 facets

- 활성 상품/활성 카테고리 조건을 항상 적용한다.
- categoryId/brandKey/basePrice 필터는 bool filter에 구성한다. 여러 브랜드는 OR, 다른 필터와는 AND다.
- name/brand/description은 같은 Nori+lowercase 분석기를 사용한다. multi_match cross_fields에서 분석된 검색어 토큰을 AND로 결합한다. 가중치는 name 4, brand 2, description 1이며 name 구문 일치를 추가 보강한다. 동의어/오타/벡터 검색은 아직 추가하지 않는다.
- 정렬과 최종 id 동점 정렬은 API 계약을 따른다. total hits는 정확한 개수를 요구한다.
- 목록/가격 집계와 모든 브랜드 집계 페이지는 한 요청 안에서 같은 PIT 스냅샷을 사용한다. PIT는 마지막에 정리하며 정리 실패 시 TTL(1분)로 만료된다.
- 브랜드는 100개씩 composite 집계를 끝까지 읽고 count 내림차순/value 오름차순으로 상위 50개를 반환한다. 반환하지 않은 브랜드가 있으면 hasMore=true다. 요청 시간 예산 내에 완료하지 못하면 부분 결과 대신 503을 반환한다. 브랜드 종류가 많으면 집계 요청 수가 늘어나므로 지연 시간을 관측해야 한다.
- 상품 페이지와 무관하게 모든 필터가 적용된 결과를 집계한다. 빈 결과의 가격은 null이다.

## 검증

로컬 Nori OpenSearch를 먼저 실행한 후 ecommerce-msa 디렉터리에서:

```bash
OPENSEARCH_TEST_ENDPOINT=http://localhost:9200 ./mvnw \
  -pl product-service/product-service-main -am \
  -Dtest=OpenSearchProductSearchIntegrationTest,OpenSearchProductSearchAdapterTest,ProductSearchConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

통합 테스트는 UUID를 포함한 임시 인덱스를 생성하고 종료 시 삭제한다. 실제 상품 인덱스/alias는 수정하지 않는다. `OPENSEARCH_TEST_ENDPOINT`를 지정하지 않으면 해당 통합 테스트만 건너뛰며 단위 테스트는 실행한다.

검증 대상: 한국어 설명/여러 필드 검색, 필드 관련도, 네 가지 정렬, 브랜드 키 정규화와 대소문자 변형의 합산, 필터 경계, 활성 상태, 빈 결과, 페이지 독립적인 집계, 여러 shards/집계 페이지의 정확한 브랜드 개수와 표시명, 인덱스 부재/잘못된 문서/통신 및 부분 응답 오류.

참고: [공식 Java 클라이언트](https://docs.opensearch.org/latest/clients/java/), [composite 집계](https://docs.opensearch.org/latest/aggregations/bucket/composite/).
