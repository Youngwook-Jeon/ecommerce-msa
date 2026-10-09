# 상품 검색 API 계약 초안

상태: 구현 전 검토용 초안. 아래 수치 제한과 동작은 이번 계약에서 제안하는 정책이며, 현재 구현을 설명하는 문서가 아니다.

## 목적과 범위

홈 네비게이션의 전체 상품 검색과 카테고리 상품 탐색을 하나의 검색 유스케이스로 통합한다. 기존 `listProductsByCategory()`와 목록/facets 계약은 이 계약으로 대체하며, 하위 호환을 위한 별도 경로는 두지 않는다.

첫 버전은 상품명·브랜드·설명 전문 검색, 관련도/최신/가격 정렬, 카테고리·브랜드·가격 필터, 페이지네이션, 브랜드/가격 facets를 제공한다. 자동완성, 초성 검색, 옵션/변형별 검색, 벡터 검색은 후속 범위다. 분석기, 동의어, 점수 가중치는 검색 품질 정책이며 HTTP 계약에 노출하지 않는다.

## 엔드포인트

- Gateway: `GET /api/v1/product_service/public/products/search`
- Product service: `GET /public/products/search`
- 인증 없는 공개 조회. 프론트엔드는 Gateway를 통한 `publicGet`을 사용한다.
- 요청/응답은 UTF-8이며 응답 Content-Type은 `application/json`이다.
- 기존 `GET /public/products`와 `GET /public/products/facets`는 전환 작업에서 제거한다. 상품 상세 `GET /public/products/{productId}`와 검색의 역할은 구분한다.

## 요청 파라미터

| 파라미터 | 타입 | 기본값 | 규칙 |
|---|---|---|---|
| `q` | string | 없음 | 정규화 후 최대 200 Unicode code points. 없음/빈 문자열/공백만 있으면 검색어 없는 탐색 |
| `categoryId` | integer | 없음 | 양의 64비트 정수. 해당 카테고리에 직접 속한 상품만 필터링하며 하위 카테고리를 자동 포함하지 않음 |
| `brands` | 반복 string | 없음 | `brands=Apple&brands=Samsung`. 정규화 후 최대 20개, 각 최대 100 code points. 쉼표는 값의 일부 |
| `minPrice` | decimal | 없음 | 0 이상, 소수점 이하 최대 2자리, `basePrice`에 대한 포함 하한 |
| `maxPrice` | decimal | 없음 | 0 이상, 소수점 이하 최대 2자리, `basePrice`에 대한 포함 상한. `minPrice <= maxPrice` |
| `sort` | enum | 조건별 결정 | `relevance`, `newest`, `price_asc`, `price_desc` |
| `page` | integer | `0` | 0부터 시작, 0 이상 |
| `size` | integer | `24` | 1~48. 범위를 벗어나면 자동 보정하지 않고 400 |

- 단일 값 파라미터의 중복 전달, 알 수 없는 파라미터, 잘못된 타입/enum은 400이다.
- 숫자 파라미터는 부호 없는 숫자 또는 선행 `-`와 숫자로 표기한다. 가격은 선택적으로 소수점과 소수부를 붙일 수 있다. 공백, 선행 `+`, 지수 표기, `.5`/`1.` 형식은 허용하지 않는다. 음수의 허용 여부와 소수 자릿수는 위 표의 값 검증 규칙을 따른다.
- `q`는 Unicode NFC 정규화, 앞뒤 공백 제거, 연속 Unicode 공백을 한 칸으로 축약한다. 길이는 정규화 후 검사한다. 검색어는 일반 텍스트로 취급하며 검색 쿼리 문법으로 실행하지 않는다.
- 브랜드는 NFC 정규화와 앞뒤 공백 제거 후 빈 값 제거 및 중복 제거를 수행한다. 필터는 정규화된 브랜드 값에 대한 대소문자 구분 없는 정확한 일치다. 첫 버전에서 브랜드 별칭은 필터 값으로 자동 변환하지 않는다.
- 서로 다른 필터는 AND, 여러 브랜드 값은 OR로 결합한다. 브랜드 없는 상품은 브랜드 필터가 없을 때만 대상이다.
- `sort` 생략 시 `q`가 있으면 `relevance`, 없으면 `newest`다. 검색어 없는 명시적 `sort=relevance`는 400이다.
- 첫 버전은 `page * size + size <= 10000`까지만 허용한다. 초과는 400이며, `totalElements`가 커도 이 범위를 넘어 탐색할 수 없다. 커서 페이지네이션은 후속 범위다.
- 존재하지 않거나 비활성인 `categoryId`는 404다. 활성 카테고리에 상품이 없으면 200의 빈 결과다.

예시:

```text
전체 검색: /public/products/search?q=무선%20이어폰
카테고리 탐색: /public/products/search?categoryId=12
필터 검색: /public/products/search?q=이어폰&brands=Apple&brands=Samsung&minPrice=50&maxPrice=200&sort=price_asc&page=0&size=24
전체 탐색: /public/products/search
```

## 검색 대상과 정렬

- 상품 `ACTIVE`이면서 소속 카테고리 `ACTIVE`인 상품만 대상으로 한다. 검색 결과, 총 개수, facets에 동일하게 적용한다.
- 문서/검색 결과 단위는 상품이며, 옵션/variant가 여러 개여도 상품은 한 번만 반환한다.
- `q`는 `name`, `brand`, `description`을 검색한다. 모든 단어의 연속 부분 문자열 일치를 요구하지 않는다. 무관한 문서를 필터만 충족한다는 이유로 반환하지 않는다.
- 관련도는 상품명·브랜드·설명 일치와 구문 일치 등을 고려한다. 구체적인 가중치와 단어 일치 기준은 대표 검색어 평가를 통해 정한다. 의미 이해/벡터 검색을 보장하지 않는다.
- `relevance`: 점수 내림차순 → 생성 시각 내림차순 → 상품 ID 내림차순.
- `newest`: 생성 시각 내림차순 → 상품 ID 내림차순.
- `price_asc` / `price_desc`: `basePrice` 오름차순/내림차순 → 상품 ID 내림차순.
- 동점 순서는 안정적으로 정하되, 서로 다른 페이지 요청 사이의 데이터 변경에 대한 스냅샷 일관성은 보장하지 않는다.

## 성공 응답

```json
{
  "query": {
    "q": "무선 이어폰",
    "sort": "relevance"
  },
  "content": [
    {
      "id": "0194a1b2-1234-7000-8000-000000000001",
      "category": {
        "id": 12,
        "name": "이어폰"
      },
      "name": "Example Wireless Earbuds",
      "brand": "Example",
      "mainImageUrl": null,
      "basePrice": 89.99
    }
  ],
  "page": 0,
  "size": 24,
  "totalElements": 1,
  "totalPages": 1,
  "facets": [
    {
      "key": "brand",
      "label": "브랜드",
      "type": "terms",
      "values": [
        { "value": "example", "label": "Example", "count": 1 }
      ],
      "hasMore": false
    },
    {
      "key": "price",
      "label": "가격",
      "type": "range",
      "min": 89.99,
      "max": 89.99
    }
  ]
}
```

- 예시에 나온 필드는 해당 객체/type에서 항상 반환한다. `query.q`, 상품의 `brand`/`mainImageUrl`, range facet의 `min`/`max`만 null을 허용한다. 빈 배열 대신 필드를 생략하지 않는다.
- `query`는 정규화된 검색어와 실제 적용 정렬을 반환한다. 필터 상태는 프론트엔드 URL이 관리한다.
- 상품 `id`는 UUID다. `category`는 필수 객체이며 `category.id`는 양의 정수, `category.name`은 카테고리 표시 이름이다. 상품 `name`은 문자열이다. 응답에 별도의 상품 `categoryId` 필드는 두지 않는다.
- 금액은 프로젝트의 단일 카탈로그 통화 단위로 표현하는 JSON number다. 가격 필터와 정렬은 옵션 추가금/variant 가격이 아닌 상품 `basePrice`를 사용한다.
- `totalElements`는 모든 검색/필터 조건에 일치하는 인덱스 상품의 정확한 총 개수다. `totalPages = ceil(totalElements / size)`이며 결과가 없으면 0이다.
- 유효한 요청에서 마지막 페이지를 넘으면 `content: []`로 반환하되 총 개수와 facets는 그대로 반환한다. 페이지 탐색 제한을 넘는 요청은 400이다.
- 빈 검색 결과는 200, `content: []`, `totalElements: 0`, `totalPages: 0`, 브랜드 값 빈 배열/`hasMore: false`, 가격 `min: null`/`max: null`이다.
- 점수, 인덱스 이름, OpenSearch 응답 구조는 공개하지 않는다.

## Facets 집계 의미

`facets`는 `type`을 판별자로 사용하는 배열이다. 첫 버전은 `brand`, `price` 순서로 두 facets를 항상 같은 응답에 포함한다. 빈 결과에서도 두 facet 객체를 유지한다. 별도의 facets 요청/선택 파라미터는 두지 않는다.

| 필드 | 공통 규칙 |
|---|---|
| `key` | 응답 내에서 유일한 안정적인 식별자. 표시 이름 변경과 무관하게 유지 |
| `label` | UI에 표시할 이름 |
| `type` | 첫 버전은 `terms` 또는 `range`. 프론트엔드/Zod는 이 값으로 구조를 구분 |
| `values`, `hasMore` | `terms`에만 필수. values의 각 항목은 문자열 `value`/`label`과 0 이상의 정수 `count` |
| `min`, `max` | `range`에만 필수. 숫자 또는 null. 둘 다 값이 있으면 `min <= max` |

서로 다른 type의 전용 필드를 빈 값으로 함께 반환하지 않는다. 배열 순서는 UI의 표시 순서이며, 프론트엔드는 순번 대신 `key`로 facet을 식별한다.

- 페이지 잘라내기 전의 전체 검색 결과에 집계한다. 검색어, 카테고리, 브랜드, 가격 필터를 모두 적용한다. 정렬과 페이지는 집계에 영향을 주지 않는다.
- 자신에 대한 필터를 제외하는 집계는 첫 버전에 도입하지 않는다. 따라서 브랜드를 선택하면 브랜드 facet도 선택된 브랜드로 좁아지고, 결과가 0이면 facets도 비어 있다.
- 브랜드 `value`는 NFC/trim 및 `Locale.ROOT` 소문자 정규화한 필터용 키, `label`은 표시용 브랜드명이다. 대소문자만 다른 브랜드는 합산한다. 표시명 후보가 여러 개면 NFC/trim한 문자열의 사전순 첫 값을 사용한다.
- 브랜드가 없거나 공백인 상품은 브랜드 facet에서 제외하지만 `totalElements`와 가격 facet에는 포함한다.
- 브랜드 값은 count 내림차순 → value 오름차순으로 최대 50개 반환한다. count는 해당 브랜드의 정확한 상품 개수이며, 생략된 브랜드가 있으면 `hasMore: true`다.
- 선택한 브랜드 상태는 URL에서 결정한다. 목록에 없는 선택 브랜드도 필터 해제 UI에서 유지한다.
- 가격은 일치 상품의 `basePrice` 최솟값/최댓값이다. 가격 구간별 개수는 첫 버전에 제공하지 않는다. 프론트엔드는 가격 범위 입력 또는 기존 프리셋을 사용한다.

## 구조화된 feature 확장

필터용 feature는 설명 문장에서 추출한 자유 텍스트가 아닌 관리되는 구조화 속성이다. feature마다 안정적인 식별자와 표시 이름을 두고, 열거형 값도 안정적인 값 식별자와 표시 이름을 분리한다. 예를 들어 연결 방식 속성은 `connectivity`, 블루투스 값은 `bluetooth`로 식별한다.

아래는 후속 feature 도입 시 추가 가능한 facet 예시다. 첫 버전 응답에는 포함하지 않는다.

```json
{
  "key": "feature:connectivity",
  "label": "연결 방식",
  "type": "terms",
  "values": [
    { "value": "bluetooth", "label": "블루투스", "count": 8 }
  ],
  "hasMore": false
}
```

- feature facet은 `feature:<안정적인 속성 식별자>`를 key로 사용한다. 속성/값의 표시 이름을 바꿔도 key/value는 유지한다. 브랜드의 소문자 정규화 규칙을 feature 식별자에 자동 적용하지 않는다.
- 열거형 feature는 `terms`, 수치형 feature는 `range` 구조를 재사용한다. 동일 type의 feature 추가는 응답의 최상위 구조 변경 없이 처리한다. 새로운 표현 type을 도입할 때는 스키마와 UI 지원을 함께 추가한다.
- 다중 값 속성의 count는 값별로 일치하는 상품 수다. 한 상품이 같은 값에 중복 집계되지 않으며, 여러 값을 가지면 각각의 값에 한 번씩 집계되므로 count 합은 totalElements를 초과할 수 있다.
- 구조화 feature는 카테고리별 적용 대상을 정의한다. 반환할 feature 목록과 순서, 여러 카테고리가 섞인 검색의 처리, 필터 요청 형식, 동일 속성 내 OR/AND 정책, 숫자 단위는 feature 기능 도입 시 별도 계약으로 정한다.
- 선택 상태는 key/value와 프론트엔드 URL로 관리한다. 지원되는 type의 새로운 key는 공통 facet UI로 표시할 수 있어야 하며, 알 수 없는 type은 UI에서 건너뛴다.
- 첫 버전에는 feature 필터 파라미터를 예약하거나 허용하지 않는다. 알 수 없는 요청 파라미터에 대한 400 규칙은 유지한다.

## 오류 응답

공통 ErrorDTO의 `{code, message}` 형태를 사용하되, 이 엔드포인트의 `code`는 프론트엔드가 분기할 수 있는 고정 값으로 정의한다. `message`의 구체적 문구는 계약에 포함하지 않는다.

| HTTP | code | 상황 |
|---|---|---|
| 400 | `INVALID_SEARCH_REQUEST` | 파라미터 타입/값/길이/조합 오류, 중복 단일 파라미터, 알 수 없는 파라미터 |
| 400 | `SEARCH_PAGE_LIMIT_EXCEEDED` | 허용된 페이지 탐색 범위 초과 |
| 404 | `CATEGORY_NOT_FOUND` | 지정 카테고리가 존재하지 않거나 비활성 |
| 503 | `SEARCH_UNAVAILABLE` | 검색 인프라 오류/타임아웃, 불완전한 검색/집계 결과 |
| 500 | `INTERNAL_ERROR` | 예상하지 못한 서비스 내부 오류 |

```json
{
  "code": "SEARCH_UNAVAILABLE",
  "message": "Product search is temporarily unavailable."
}
```

검색 실패를 빈 성공 응답으로 변환하거나 PostgreSQL 검색으로 자동 대체하지 않는다. 상품 목록/총 개수/facets 중 일부만 성공한 응답도 반환하지 않는다. 상세 장애 정보는 서버 로그에만 남긴다. 이 오류 코드 정책은 검색 엔드포인트에 적용하며 다른 API 오류 계약을 함께 변경하지 않는다.

## 데이터 반영과 프론트엔드 연결

- 응답은 검색 인덱스의 상태를 기준으로 하며 DB 변경이 즉시 반영됨을 보장하지 않는다. 삭제/비활성화도 동기화 지연 중 노출될 수 있다. 엄격한 즉시 비노출 요구는 별도 정책으로 결정해야 한다.
- 검색 문서에 카테고리 ID와 이름을 포함한다. 카테고리 이름 변경 시 소속 상품의 검색 문서를 갱신하며, 응답 이름에도 인덱스 동기화 지연이 적용된다.
- 인덱스 동기화 지연 목표와 관측 지표는 후속 인덱싱 설계에서 정한다. 상품 상세/주문 검증은 해당 API에서 수행한다.
- 홈 검색창은 Enter/검색 버튼으로 `/search?q=...`에 이동한다. 빈 입력 제출 시 이동하지 않는다. `/search` 직접 접근은 전체 탐색으로 처리한다.
- 카테고리 화면은 같은 API에 `categoryId`를 전달한다. URL이 검색어·필터·정렬·페이지 상태를 보존하며, 검색어/필터/정렬/size 변경 시 page를 0으로 초기화한다.
- URL의 page는 API와 동일한 0-based 값, 화면 페이지 번호는 1-based 값으로 표시한다. 탐색 한계를 넘는 페이지 링크는 만들지 않는다.
- 검색어 유무 변경 시 기본 정렬을 다시 계산하고, 검색어 없는 `relevance` 요청은 생성하지 않는다.
- 검색 서비스와 Zod 스키마를 새 응답에 맞춰 변경한다. 기존 목록/facets 호출과 사용하지 않는 DTO/포트/저장소 구현은 전환 시 제거한다.
- 상품 카드의 카테고리 표시는 `category.name`, 카테고리 링크는 `category.id`를 사용한다. facet UI는 key로 상태를 식별하고 type에 따라 공통 컴포넌트를 선택한다. 알려진 type은 구조를 검증하고, 알 수 없는 type은 건너뛸 수 있게 스키마 파싱을 설계한다.
- UI는 결과 없음, 잘못된 요청, 카테고리 없음, 검색 불가를 구분한다. 검색 불가에는 재시도를 제공한다.

## 후속 구현의 계약 검증 사례

1. q/categoryId 없는 전체 탐색과 categoryId만 있는 탐색의 기본 정렬은 newest다.
2. 설명에만 있는 단어와 여러 단어 검색을 지원하고 기본 정렬은 relevance다.
3. 공백/NFC 정규화, 빈 q, 반복 브랜드 OR와 서로 다른 필터 AND를 확인한다.
4. 가격 경계 포함, 잘못된 가격 범위/enum/size, 없는 카테고리와 페이지 탐색 한계를 확인한다.
5. 비활성 상품/카테고리 제외, 상품 중복 제거, 동점 정렬을 확인한다.
6. 페이지와 무관한 facets/총 개수, 모든 필터 적용, null 브랜드와 빈 결과를 확인한다.
7. 브랜드 facet 정규화/정확한 count/50개 초과/hasMore와 선택 필터 해제 UI를 확인한다.
8. 검색/집계 실패가 503으로 반환되고 결과 없음과 구분되는지 확인한다.
9. 상품의 category 객체에 ID/이름이 포함되고 카테고리 이름 변경이 인덱스 동기화 후 반영되는지 확인한다.
10. facets 배열의 key 유일성, type별 필수 필드, 빈 결과에서의 두 facet 유지, 지원 type의 새로운 key 렌더링과 알 수 없는 type 건너뛰기를 확인한다.

## 구현 진행 상황

- web: `PublicProductSearchRequest`와 `PublicProductSearchRequestMapper`가 전체 HTTP 파라미터 맵에서 요청을 읽고, 알 수 없는/중복 단일 파라미터 및 잘못된 숫자 표기를 거부한다.
- application: `ProductSearchQueryValidator`가 raw `ProductSearchQuery`를 정규화/검증하여 `ProductSearchCriteria`를 만든다. 기존 목록 조회의 검증 규칙과 분리한다.
- web: `PublicProductSearchResponse`와 `PublicProductSearchFacetResponse`가 category 객체 및 `terms`/`range` JSON 구조를 표현한다.
- 검증 실패는 `ProductSearchRequestException`과 전용 예외 처리 메서드로 계약의 400 오류 코드를 반환한다.
- 단위 테스트와 테스트 전용 HTTP 바인딩 테스트로 기본값, Unicode 정규화/길이, 브랜드 중복, 가격/페이지 제한, 오류 코드, 응답 직렬화를 검증한다.
- `PublicProductSearchController`가 `GET /public/products/search`를 제공하며, `ProductSearchService`에 요청을 전달한다. 서비스는 요청 검증 후 지정된 카테고리의 존재/활성 여부를 확인하고 `ProductSearchPort`를 호출한다. 전체 탐색에는 카테고리 존재 확인을 수행하지 않는다.
- `ProductSearchResult`는 상품 목록과 정확한 총 개수, 브랜드/가격 집계를 표현한다. 서비스가 총 페이지를 계산하고 `PublicProductSearchResponseMapper`가 공개 응답으로 변환한다. 생성일은 응답에 포함하지 않는다.
- 검색 전용 예외 처리로 `CATEGORY_NOT_FOUND`(404), `SEARCH_UNAVAILABLE`(503), `INTERNAL_ERROR`(500)를 반환한다. 검색 엔드포인트의 500 코드 정책은 다른 API에 적용하지 않는다.
- OpenSearch 어댑터가 아직 없으므로 `ProductSearchConfiguration`은 검색 포트가 없는 경우에만 검색 불가 구현을 등록한다. 유효한 검색 요청은 현재 `503 SEARCH_UNAVAILABLE`을 반환하며, 빈 결과나 PostgreSQL 검색으로 대체하지 않는다. 실제 `ProductSearchPort` 구현이 등록되면 검색 불가 구현은 등록하지 않는다.

검색 엔드포인트는 이제 노출되지만 실제 상품 검색을 위해서는 OpenSearch 어댑터와 인프라/인덱싱 연결이 필요하다. 기존 목록/facets API와 프론트엔드의 제거/전환은 해당 연결 후 수행한다. 새 경로는 기존 Gateway의 공개 경로 규칙을 사용하며 포트/환경 변수/이벤트 토픽 변경은 없다.
