# 관리자 상품 변경 버전 계약

같은 상품을 여러 화면에서 편집할 때 오래된 변경으로 덮어쓰지 않도록 상품 단위의 낙관적 버전을 사용한다. 상품·상품 옵션·variant·상품 이미지·옵션 값 이미지가 하나의 버전을 공유한다.

## 조회와 요청

`GET /admin/queries/products/{productId}` 응답에 양의 정수 `version`을 포함한다. 상품 내용과 버전은 같은 DB 스냅샷의 값이다. 목록에는 버전을 추가하지 않으며, 편집·상태 변경·삭제 화면을 열 때 상세를 조회한다.

상품 생성과 presign-upload를 제외한 `/admin/products/{productId}/...` 변경 요청은 **쿼리 파라미터 expectedVersion**을 필수로 전달한다. PUT/PATCH/POST/DELETE에 같은 방식을 사용한다. 기존 JSON 본문은 유지한다.

```http
GET /admin/queries/products/01900000-0000-7000-8000-000000000001

HTTP/1.1 200 OK
Content-Type: application/json

{"id":"01900000-0000-7000-8000-000000000001","version":7,"name":"상품명", "...":"기존 상세 필드"}
```

```http
PUT /admin/products/01900000-0000-7000-8000-000000000001?expectedVersion=7
Content-Type: application/json

{"name":"수정 상품명","description":"상품 설명은 기존 검증 규칙을 충족해야 합니다.","basePrice":10000,"brand":"브랜드","categoryId":1}
```

적용 범위는 기본 정보 수정, 상태 변경, soft delete, 옵션 그룹/값 추가·삭제·순서 변경·visual 설정, variant 추가·수정·삭제, 상품/옵션 값 이미지 commit·삭제·정렬이다. 글로벌 옵션 사전·카테고리 자체의 CRUD와 재고 예약/주문 흐름에는 이 계약을 적용하지 않는다.

## 결과와 충돌

- 누락, 파싱 불가, 0 이하의 expectedVersion은 400이다. 서버가 현재 버전을 대신 채우지 않는다.
- 조회 당시와 현재 버전이 다르거나 처리 중 다른 변경이 먼저 확정되면 409이다.
- 오류 코드는 `PRODUCT_VERSION_CONFLICT`, 메시지는 `The product has changed. Reload the latest product before editing.`이다.
- 충돌 시 상품·하위 리소스 변경·버전·이력·outbox는 함께 롤백한다. 충돌 요청의 커밋 후 캐시 무효화도 실행하지 않는다.
- 실제 변경은 버전을 한 번 증가시킨다. 최초 생성은 1이다. 검증만 수행한 no-op은 증가시키지 않지만 기대 버전 검증은 수행한다.
- 기존 변경 성공 응답의 본문 구조는 유지한다. 후속 편집에는 상세를 다시 조회해 받은 버전을 사용한다.

## 관리자 화면 동작

화면에서 로드한 상품과 그 version을 함께 보관하고 제출한다. 제출 직전에 버전만 새로 조회해 교체하면 오래된 화면의 변경을 감지할 수 없으므로 금지한다. 409는 자동 재시도/병합하지 않고 관리자에게 다시 조회하도록 안내한다.

기존 관리자 화면은 상세 조회 버전을 각 변경 요청으로 전달한다. 상품 편집에는 최신 상품을 다시 불러오는 버튼을 제공한다. 이미지 변경 중에는 기본 정보 편집을 막고, 기본 정보에 저장하지 않은 변경이 있으면 이미지 변경을 막아 서로 다른 버전의 편집 내용을 섞지 않는다. 이미지 일괄 업로드는 동일 상품의 커밋을 순차 실행하며 성공한 새 이미지마다 버전을 증가시킨다. 실패한 커밋 이후에는 남은 커밋을 중단하고, 처리 결과를 다시 조회한다.

## 저장소와 배포

V17은 기존 `product_search_sync_state`를 `product_catalog_versions`로, outbox의 `search_revision`을 `catalog_revision`으로 이름만 변경한다. 기존 버전 값과 이력은 유지한다. Java 저장소는 검색 패키지 밖의 `dataaccess.catalog.ProductCatalogRevisionStore`가 담당한다.

OpenSearch 문서는 `catalogRevision`을 external version으로 사용한다. 아직 매핑을 적용하지 않은 기존 결정에 따라 매핑 버전은 1을 유지하며, 최초 인덱스는 변경된 JSON으로 생성한다. Kafka 메시지의 리비전 필드는 catalog_revision을 사용한다. 검색 consumer와 캐시 consumer의 group, DLT 토픽과 재시도 정책은 유지한다.
