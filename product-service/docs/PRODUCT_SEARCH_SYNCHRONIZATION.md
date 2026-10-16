# 상품 검색 변경 동기화

## 범위

상품 생성, 상품 수정(카테고리 지정/변경 포함), 상태 변경, soft delete와 기존 옵션·variant·이미지 변경 이벤트를 검색에 반영한다. 기존 `product.catalog.changed` 토픽을 사용하며 캐시 무효화와 독립된 `product-service-search-sync` consumer group으로 소비한다. 이벤트의 필드로 검색 문서를 조립하지 않고 PostgreSQL의 최신 상품을 재조회한다.

카테고리 자체의 상태 변경에 따른 관련 상품 갱신과 별도 카테고리 outbox/토픽은 이번 범위에서 제외한다. 카테고리 이름은 검색 문서에 저장하지 않는다. 물리 삭제도 지원하지 않는다.

## revision과 간단한 변경 이력

V16 마이그레이션에서 다음 구조를 추가한다.

| 저장소 | 역할 |
|---|---|
| `product_catalog_versions` | 상품별 현재 revision. product_id가 PK/FK이며 기존 상품은 1로 시작 |
| `product_catalog_change_history` | event_id, product_id, revision, change_type, occurred_at, changed_by 저장. 상품/revision 및 event_id 중복 금지 |
| `product_catalog_outbox.catalog_revision` | 새 이벤트에 해당 revision 전달. 이전 이벤트의 null도 소비 가능 |

상품 생성은 revision 1, 이후 실제 변경 이벤트마다 1씩 증가한다. 상품 저장 어댑터의 insert()는 상품을 flush하고 같은 트랜잭션에서 버전 행을 초기화한다. V16은 기존 상품의 revision을 생성하며, V17은 테이블을 product_catalog_versions로, outbox 열을 catalog_revision으로 변경한다.

`ProductCatalogChangeService.recordChange()`는 기대 버전이 포함된 변경 이벤트를 outbox 어댑터에 전달하고 커밋 후 캐시 무효화를 예약한다. 검색 문서에서는 catalogRevision, Kafka JSON 메시지에서는 catalog_revision을 사용한다.

### 낙관적 버전 검증

관리자 상세 조회는 상품과 버전을 같은 REPEATABLE_READ 스냅샷에서 읽어 version을 반환한다. 기존 상품 변경 API는 모두 필수 쿼리 파라미터 expectedVersion을 받는다. 생성과 업로드용 presign은 제외한다. 자세한 계약은 [관리자 상품 버전 계약](ADMIN_PRODUCT_CATALOG_VERSIONING.md)을 참고한다.

변경 서비스는 상품을 읽기 전에 전달된 버전을 검증한다. 따라서 no-op 요청이나 이미 처리된 삭제 요청도 오래된 버전이면 409로 거절한다. 일반 상품 조회에는 명시적 행 잠금이 없으며 findByIdForUpdate/findAllByIdForUpdate와 lockAll은 제거했다.

실제 변경 시 outbox 어댑터가 다음 조건부 UPDATE로 최종 충돌을 감지한다.

```sql
UPDATE product_catalog_versions
SET revision = revision + 1
WHERE product_id = ? AND revision = ?
RETURNING revision;
```

갱신된 행이 없으면 ProductVersionConflictException을 던진다. 도메인 검증과 변경 계산 후 실제 상품·하위 리소스 쓰기 전에 recordChange()를 호출해 버전을 확정하고 간단한 이력과 outbox를 저장한다. 모든 경로에서 버전 갱신 → 상품/하위 리소스 쓰기 순서를 유지한다. 이후 JPA/JDBC/JPQL 저장이 실패하면 버전·이력·outbox까지 같은 트랜잭션에서 롤백된다. 생성은 이미 초기화된 revision 1을 기록하며 증가시키지 않는다. no-op은 버전을 증가시키거나 outbox를 발행하지 않는다.

조회 검증 후 다른 요청이 커밋하더라도 조건부 UPDATE가 최종 방어선이다. 실패 요청을 자동 재시도하거나 최신 버전으로 바꿔 다시 제출하지 않는다. 여러 상품을 한 트랜잭션에서 변경하는 기능을 추가할 때도 각 상품의 기대 버전을 전달하고, UPDATE 및 실제 데이터 변경 순서를 상품 ID 오름차순으로 맞춰야 한다. 조건부 UPDATE도 커밋까지 쓰기 락을 유지하므로 낙관적 방식이 DB의 모든 대기나 데드락 가능성을 없애는 것은 아니다.

changed_by는 인증된 JWT subject를 사용하고, JWT가 아닌 인증은 인증 이름을 사용한다. 인증 정보가 없는 처리는 `SYSTEM`이다. 변경 전후 값, 상세 diff, 상세 감사 조회 API는 구현하지 않는다. 기존 상품의 과거 이력도 소급 생성하지 않는다.

## 문서 생성과 역순 이벤트

컨슈머는 짧은 `REQUIRES_NEW/readOnly/REPEATABLE_READ` 트랜잭션으로 상품·카테고리와 revision을 같은 스냅샷에서 읽는다. 이벤트 revision보다 DB revision이 작으면 처리를 보류한다. 이벤트가 오래되었더라도 현재 DB revision으로 문서를 생성한다. OpenSearch 통신 중에는 DB 트랜잭션을 유지하지 않는다.

초기 적재와 변경 동기화 모두 `catalogRevision`을 OpenSearch의 strict external version으로 사용한다. 동일하거나 오래된 버전으로 발생한 `version_conflict_engine_exception` 409는 이미 반영된 성공으로 취급한다. 다른 409나 매핑 오류는 성공으로 취급하지 않는다. 재시도와 DLT 재처리가 최신 문서를 과거 상태로 덮어쓰지 않는다.

카테고리가 없는 상품은 `documentReady=false`인 최소 문서(id, 상품 상태, 생성 시각, revision)를 저장한다. 검색용 상세 필드는 색인을 보류하며, 이 최소 문서가 과거의 노출 가능한 문서를 덮어쓴다. 이후 카테고리가 지정된 변경 이벤트에서는 완전한 문서와 `documentReady=true`를 저장한다. 공개 검색은 항상 documentReady=true, 상품 ACTIVE, 카테고리 ACTIVE를 함께 요구한다. 비활성·soft delete도 문서를 삭제하지 않고 최신 버전의 상태로 저장한다.

매핑 버전은 아직 적용 전인 기존 결정에 따라 1을 유지한다. `catalogRevision`과 `documentReady`가 추가된 매핑으로 최초 인덱스를 생성해야 한다.

## 활성 인덱스와 작업 전환

컨슈머는 PostgreSQL의 ACTIVE 버전이 가리키는 **물리 인덱스**에 쓴다. 읽기 alias의 실제 대상과 메타데이터가 일치하는지 확인하고, Bulk 재시도 전과 완료 후에도 ACTIVE 대상이 유지되는지 확인한다. ACTIVE 버전이 없거나 ACTIVATING 상태가 존재하거나 alias가 일치하지 않으면 정상 대기로 처리한다.

이 동기화만으로 온라인 재색인을 지원하지는 않는다. 초기 적재 시작부터 새 인덱스 활성화 완료까지 상품·카테고리 쓰기를 중단하는 기존 운영 조건을 유지한다. 새 인덱스의 변경분 추적/재생과 전환 시점의 쓰기 조정은 후속 작업이다.

## 재시도와 DLT

- 자동 offset commit을 끄고 RECORD ack를 사용한다. 성공하거나 DLT 발행이 확인된 레코드만 진행한다.
- 정상 대기는 컨테이너를 일시 정지하며 5초 간격으로 계속 재시도한다. 해당 컨테이너의 후속 레코드도 대기하며 DLT로 보내지 않는다.
- 실제 처리 오류는 1초 간격으로 3회 재시도 후 `product.catalog.changed.search.DLT`에 원래 partition으로 발행한다. DLT partition 수는 원본 이상이어야 한다. 로컬 초기화 설정은 양쪽 모두 3개다.
- DLT 발행 실패는 offset 진행을 막는다. 검색 DLT용 producer/error handler는 다른 consumer 설정과 분리한다.
- DLT 재처리 listener는 기본 비활성화다. 원인을 해결한 뒤 `PRODUCT_SEARCH_SYNC_DLT_REPLAY_ENABLED=true`로 실행하면 별도 group `product-service-search-sync-dlt-replay`가 최신 상품을 재조회한다. 실패 시 계속 대기하며 DLT로 다시 발행하지 않는다. 중복 실행은 external version으로 처리한다.
- 실패 로그에는 eventId/productId/eventRevision을, 성공 로그에는 productId/revision/indexVersionId/index/documentReady를 포함한다.

## 설정과 실행

| 환경 변수 | 기본값 | 역할 |
|---|---|---|
| `PRODUCT_SEARCH_SYNC_ENABLED` | `true` | 검색 변경 consumer 활성화 |
| `PRODUCT_SEARCH_SYNC_DLT_TOPIC` | `product.catalog.changed.search.DLT` | 검색 전용 DLT |
| `PRODUCT_SEARCH_SYNC_DLT_REPLAY_ENABLED` | `false` | 수동 DLT 재처리 활성화 |

검색 자체가 비활성화이면 consumer도 생성하지 않는다. 이번 consumer는 기존 기본값인 Debezium relay에서 동작한다. polling relay를 통한 검색 동기화는 지원하지 않는다. Debezium의 기존 테이블 CDC가 추가된 catalog_revision 열을 전달하며 새로운 connector/원본 토픽은 필요하지 않다.

기존 환경에는 V16과 새 매핑을 적용하고 어드민 API로 초기 적재·활성화한다. 실행 중인 Kafka에는 새 DLT 토픽을 별도로 생성한다. `init_kafka.yml`은 기존 토픽 삭제 명령도 포함하므로 DLT 추가만을 위해 실행하지 않는다.

## 검증

로컬 Nori OpenSearch를 실행한 뒤 ecommerce-msa 디렉터리에서 실행한다. PostgreSQL과 Kafka 통합 테스트는 Testcontainers를 사용한다.

```bash
OPENSEARCH_TEST_ENDPOINT=http://localhost:9200 ./mvnw \
  -pl product-service/product-service-main -am \
  -Dtest=ProductSearchSynchronizationIntegrationTest,OpenSearchProductSynchronizationAdapterTest,ProductSearchKafkaIntegrationTest,ProductSearchSynchronizationListenerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

트랜잭션 롤백과 outbox 실패, 동일 버전의 동시 변경 중 하나만 커밋되는 동작, 늦은 이벤트/중복, documentReady 전환과 soft delete, Kafka 대기 시 offset 유지, 오류의 DLT 이동 및 DLT 재처리 시 재발행 방지를 검증한다. OpenSearch를 지정하지 않으면 해당 실제 인덱스 테스트만 건너뛴다. 테스트용 인덱스/alias는 고유 이름을 사용하고 종료 시 삭제한다.
