# 어드민 상품 검색 인덱스 관리 계약

## 이번 단계의 범위

상태 모델·스키마·DTO에 더해 적재 요청 등록(POST /index-builds), 작업 조회(GET /index-builds/{jobId}), 버전 목록(GET /index-versions)을 controller → application service → PostgreSQL adapter로 연결한다. DB 작업 선점·lease·실행기, 로더 진행률 반영, OpenSearch 인덱스 생성과 검증까지 연결한다. 활성화 작업 모델·영속 엔티티·V13/V14 스키마, 활성화 등록·조회 라우트와 alias 전환 실행기까지 연결한다. 미확정 활성화의 자동 관측·성공 확정·최근 이력 조회·수동 재확인도 연결한다. alias 재전송이나 강제 실패 확정·수동 전환 기능은 포함하지 않는다. 실행기가 켜져 있으면 QUEUED 작업을 처리해 SUCCEEDED/READY까지 진행한다.

첫 버전은 적재 시작부터 검증과 활성화 완료까지 상품·카테고리 쓰기가 멈춰 있어야 한다. catalogWritesPaused=true는 관리자의 확인이며 실제 쓰기 차단 장치를 대신하지 않는다. 변경 이벤트 동기화와 온라인 재색인 조정은 후속이다.

## 모델과 상태

인덱스 버전·적재 작업·활성화 작업 모델과 상태 enum은 product-domain-application의 `application.searchindex.model`에 둔다. 검색 인덱스 운영 생명주기는 상품 domain-core의 비즈니스 애그리거트와 분리한다. JPA 엔티티와 repository는 product-dataaccess에 유지하며 모델에는 Spring/JPA/OpenSearch 타입을 사용하지 않는다.

### 인덱스 버전

`ProductSearchIndexVersion`은 UUID id와 단조 증가하는 versionNumber를 분리한다. versionNumber는 PostgreSQL sequence에서 발급하고 실제 이름은 서버가 products-v{versionNumber}로 생성한다. 순번에 빈 번호가 생길 수 있다. 요청이 실제 인덱스 이름/매핑 내용을 지정하지 않는다.

`product-service.search.mapping.resource` 설정(환경 변수 `PRODUCT_SEARCH_MAPPING_RESOURCE`, 기본값 `classpath:opensearch/products-index.json`)으로 매핑 JSON 위치를 지정한다. 등록 시 해시 계산과 실행기의 인덱스 생성은 같은 설정을 사용한다. `classpath:` 또는 `file:` 리소스를 사용할 수 있다. 등록 이후 파일 내용이나 설정이 달라져 해시가 일치하지 않으면 실행기는 인덱스를 생성하지 않고 작업을 실패 처리한다.

mappingVersion은 저장소에서 관리하는 매핑 정의의 버전이며 mappingHash는 사용한 매핑 JSON 파일 바이트의 SHA-256 소문자 hex(64자)다. 같은 매핑으로 여러 물리 인덱스를 만들 수 있다. requestedBy는 JWT의 sub이며 요청 body에서 받지 않는다. 기존 인증 converter의 principal name은 preferred_username이므로 Authentication.getName() 대신 JWT subject를 직접 사용한다.

| 상태 | 의미 | 다음 상태 |
|---|---|---|
| REGISTERED | DB에 버전 등록, OpenSearch 생성 전 | BUILDING, FAILED |
| BUILDING | 인덱스 생성/적재/검증 중 | READY, FAILED |
| READY | 적재와 검증 완료, 공개 검색 연결 전 | ACTIVATING |
| ACTIVATING | 활성화 의도 기록, 실제 alias 결과 확인 중 | ACTIVE / 전환 전 실패 시 READY |
| ACTIVE | products-read가 이 인덱스 하나를 가리킨다고 확인 | INACTIVE |
| INACTIVE | 과거 활성 버전, 이력 보관(첫 버전은 재활성화하지 않음) | — |
| FAILED | 생성/적재/검증 실패 | 없음 |

OpenSearch 호출 타임아웃으로 alias 결과가 불명확하면 ACTIVATING을 유지한다. 임의로 ACTIVE/FAILED로 바꾸지 않고 실제 alias 확인·조정으로 해소해야 한다. 변경 동기화가 없는 첫 버전은 READY만 활성화하며 INACTIVE 재활성화는 허용하지 않는다. 기존 ACTIVE를 INACTIVE로 바꾸고 새 버전을 ACTIVE로 바꾸는 DB 기록은 한 트랜잭션에서 수행한다.

### 적재 작업

`ProductSearchIndexBuildJob`은 버전 하나에 대한 한 번의 적재 시도다. UUID job id, indexVersionId, requestedBy, idempotencyKey, batchSize(1~1000), 상태, 진행 수, 시각과 안전한 실패 정보를 저장한다.

QUEUED → RUNNING → VERIFYING → SUCCEEDED 흐름이며 진행 중 FAILED로 종료할 수 있다. RUNNING/VERIFYING 작업의 실행 소유권이 유실됐다고 확인한 경우 INTERRUPTED로 종료한다. 단순히 프로세스가 시작됐다는 이유로 다른 서버의 작업을 중단 처리하지 않는다. 소유권/lease 구현은 후속이다. terminal 상태를 다시 RUNNING으로 바꾸지 않고 새 요청 키와 새 버전으로 다시 적재한다.

- sourceProductCount: 시작 시 전체 DB 상품 수. QUEUED에서는 null.
- indexedProducts/completedBatches: Bulk의 모든 항목이 성공한 배치만 누적. 실패 배치의 부분 성공은 포함하지 않는다. 실시간 OpenSearch 문서 수를 뜻하지 않는다.
- startedAt: 작업 실행 시작 시각. finishedAt: 성공/실패/중단 종료 시각.
- failureCode/failureMessage: 실패·중단 상태에서만 필수. 안전한 코드와 최대 500자 요약이며 SQL/문서 내용/자격 증명/stack trace를 반환하지 않는다.
- 성공 전 sourceProductCount, 적재 수, 최종 DB 상품 수, refresh 이후 인덱스 문서 수가 같아야 한다. 검색 검증도 통과한 후에만 SUCCEEDED/READY를 기록한다.

## PostgreSQL 제약

product_search_index_versions와 product_search_index_build_jobs 테이블을 사용한다. DB에는 PK/FK/NOT NULL, 버전 번호·물리 이름·버전별 작업 유일성, (requested_by, idempotency_key) 유일성과 동시성 제약만 둔다. 상태별 필수 값, 시각·개수 관계, 값 범위와 문자열 형식은 application 모델에서 검증하며 CHECK 제약으로 중복하지 않는다. 두 테이블의 id는 다른 UUID 테이블과 동일하게 DEFAULT uuidv7()를 사용한다. INSERT에서 id를 생략했을 때 DB 기본값이 적용되며 명시적으로 전달한 UUID는 그대로 저장한다. 각 엔티티의 lockVersion은 JPA @Version으로 동시 갱신을 감지한다.

부분 unique index로 전체 QUEUED/RUNNING/VERIFYING 작업은 최대 하나, ACTIVE 버전은 최대 하나, ACTIVATING 버전은 최대 하나로 제한한다. 중복 클릭/여러 서버의 요청에도 DB 제약이 최종 방어선이다. 등록 서비스는 버전과 작업을 한 트랜잭션에 저장하고 멱등성 재조회/unique 충돌을 API 오류로 변환해야 한다. 상태 전이 자체는 application의 검색 인덱스 관리 모델을 거쳐 수행한다.

두 테이블의 상태를 개별적으로 저장하지 않고 작업 claim과 버전 BUILDING, 작업 SUCCEEDED와 버전 READY, 실패 작업과 버전 FAILED를 각각 같은 DB 트랜잭션으로 갱신한다. DB 상태 변경과 OpenSearch 호출은 별개의 작업이다.

## HTTP 계약

Backend prefix: /admin/product-search. Gateway prefix: /api/v1/product_service/admin/product-search. 모든 API는 기존 규칙과 같은 hasAuthority('ADMIN')을 요구한다. 브라우저 mutation은 Gateway 세션과 CSRF 처리를 유지한다.

### POST /index-builds

필수 헤더: Idempotency-Key. ASCII [A-Za-z0-9._:-]로 1~128자다. 관리자별로 범위를 구분한다.

```json
{ "batchSize": 500, "catalogWritesPaused": true }
```

batchSize 생략/null은 500. catalogWritesPaused는 필수 true다. 실제 인덱스 이름, 버전 번호, 매핑, 관리자 식별자는 서버에서 결정한다. 현재 제공되는 매핑만 사용한다.

동일 관리자·동일 키·동일 유효 요청(batchSize 및 확인 값)은 동일 작업을 반환한다. 다른 batchSize로 키를 재사용하면 409 IDEMPOTENCY_KEY_CONFLICT다. 이미 실패한 작업도 동일 키로 다시 실행하지 않는다. 멱등성 확인을 진행 중 작업 충돌 검사보다 먼저 수행한다.

응답 202 Accepted, Location은 backend 기준 /admin/product-search/index-builds/{jobId}다. Gateway 클라이언트는 위 Gateway prefix를 적용해 조회한다.

```json
{
  "jobId": "01940000-0000-7000-8000-000000000001",
  "indexVersionId": "01940000-0000-7000-8000-000000000002",
  "status": "QUEUED"
}
```

재요청 시 기존 작업의 현재 상태를 반환한다. 202는 요청 등록 의미이며 적재 성공을 뜻하지 않는다. 작업을 DB에 커밋한 후에만 반환한다.

### GET /index-builds/{jobId}

응답 200. 다음 필드는 항상 존재하며 nullable 값도 필드를 생략하지 않는다.

```json
{
  "id": "01940000-0000-7000-8000-000000000001",
  "indexVersionId": "01940000-0000-7000-8000-000000000002",
  "batchSize": 500,
  "status": "RUNNING",
  "progress": { "sourceProductCount": 1200, "indexedProducts": 500, "completedBatches": 1 },
  "requestedBy": "keycloak-subject",
  "createdAt": "2026-10-12T00:00:00Z",
  "updatedAt": "2026-10-12T00:01:00Z",
  "startedAt": "2026-10-12T00:00:01Z",
  "finishedAt": null,
  "failure": null
}
```

QUEUED의 sourceProductCount/startedAt은 null이며 처리 수는 0이다. 실패 시 failure는 {"code":"BULK_ITEM_FAILED","message":"상품 색인에 실패했습니다."} 형태다. 시각은 UTC ISO-8601, 개수는 0 이상 정수다.

### GET /index-versions

page 기본 0, size 기본 20(1~100). createdAt 내림차순 → id 내림차순으로 고정 정렬한다. 마지막 페이지 초과는 빈 content와 실제 총 개수를 반환한다. 빈 결과 totalPages는 0이다.

응답 구조는 content/page/size/totalElements/totalPages다. content 항목은 id, versionNumber, indexName, mappingVersion, mappingHash, status, requestedBy, createdAt, updatedAt, activatedAt이다. 활성화된 적 없는 버전의 activatedAt은 null이다. INACTIVE에도 과거 activatedAt이 유지된다.

### POST /index-versions/{versionId}/activate

필수 Idempotency-Key 헤더와 아래 body를 사용한다.

```json
{ "expectedActiveVersionId": null, "catalogWritesPaused": true }
```

expectedActiveVersionId는 관리자가 조회한 현재 활성 버전 UUID다. null은 현재 활성 버전이 없어야 한다는 조건이며 조건 검사를 생략하는 의미가 아니다. 실제 alias와 DB 기록이 기대 상태와 다르면 409 ACTIVE_INDEX_CHANGED 또는 INDEX_STATE_UNCONFIRMED다.

READY만 요청 가능하며 진행 중 적재 작업이나 미완료 활성화 작업이 있으면 거부한다. 동일 활성화 요청의 멱등성은 적재 요청과 별도로 관리한다. 활성화 요청 키·기대 버전·관리자·alias 이름·실행 의도는 ProductSearchIndexActivationJob에 영속적으로 기록한다. 등록은 현재 DB 활성 버전과 실제 alias가 일치하는지 확인한 뒤 공통 advisory lock으로 상태를 다시 검사한다. 네트워크 조회 중에는 DB 트랜잭션을 유지하지 않는다. 등록과 실행 사이 상태 변경에 대비해 실행기가 전환 직전에 다시 확인한다.

응답 202는 활성화 의도를 수락했다는 의미다. 예: {"jobId":"01940000-0000-7000-8000-000000000003","indexVersionId":"01940000-0000-7000-8000-000000000002","expectedActiveVersionId":null,"status":"QUEUED"}. status는 활성화 작업 상태다. 등록 트랜잭션에서 대상 인덱스 버전은 ACTIVATING으로 바뀐다. Location은 /admin/product-search/index-activations/{jobId}다. 동일 관리자·키·대상·기대 버전 요청은 현재 작업을 그대로 반환하며, 대상이나 기대 버전이 다르면 409 IDEMPOTENCY_KEY_CONFLICT다. 완료된 요청의 재전송도 alias 조회 없이 기존 작업을 반환한다.

### GET /index-activations/{jobId}

200 응답은 id/indexVersionId/expectedActiveVersionId/aliasName/status/requestedBy/createdAt/updatedAt/startedAt/switchStartedAt/finishedAt/failure/latestReconciliation을 포함한다. failure는 null 또는 {code,message}이며 ownerToken/leaseUntil은 공개하지 않는다. 없는 작업은 404 INDEX_ACTIVATION_NOT_FOUND다. 활성화 완료는 작업 SUCCEEDED와 버전 ACTIVE로 확인한다.

## 활성화 작업 모델

ProductSearchIndexActivationJob은 application.searchindex.model에 위치한 프레임워크 독립 모델이다. 대상 indexVersionId, nullable expectedActiveVersionId, aliasName, requestedBy, idempotencyKey, 상태, 생성·갱신·시작·전환 시작·완료 시각, 안전한 failureCode/failureMessage를 기록한다. aliasName은 등록 당시 설정을 보존해 실행 시 설정 변경으로 다른 alias를 전환하지 않게 한다. expectedActiveVersionId=null은 초기 활성화 조건이며 기대 상태 검사를 생략하지 않는다. 대상과 기대 활성 버전이 같으면 생성할 수 없다.

| 작업 상태 | 의미 | 허용 전이 |
|---|---|---|
| QUEUED | 활성화 의도 등록, 실행 전 | RUNNING / FAILED |
| RUNNING | 실행 선점 및 전환 전 조건 확인 | SWITCHING / FAILED |
| SWITCHING | 전환 요청을 보낼 수 있다는 의도를 DB에 먼저 기록 | SUCCEEDED / UNCONFIRMED |
| UNCONFIRMED | 전환 결과 확인 필요, 미완료 상태 | 실제 alias 확인 후 SUCCEEDED |
| SUCCEEDED | alias가 대상 버전 하나만 가리킨다고 확인 | — |
| FAILED | alias 전환 요청 전에 종료된 실패 | — |

beginSwitch() 상태를 영속적으로 저장한 뒤에만 alias 변경 요청을 전송해야 한다. confirmActivation()은 확인한 활성 버전 ID가 대상과 일치할 때만 성공하며, alias가 여러 인덱스를 가리키는지 확인하는 일은 추후 어댑터의 책임이다. SWITCHING 이후에는 failBeforeSwitch()를 사용할 수 없다. 타임아웃·실행기 중단·DB 기록 실패로 결과가 불확실하면 UNCONFIRMED 또는 아직 저장된 SWITCHING 상태를 유지하고, 인덱스 버전은 ACTIVATING에 둔다. 이전 인덱스를 가리키는 alias를 한 번 관측한 것만으로는 지연 중인 전환 요청이 없다고 보장할 수 없으므로 미확정 작업을 자동 실패 처리하거나 재전송하는 기능은 포함하지 않는다. 대상 확인에 따른 성공 확정은 아래 조정기가 수행한다. 이전 상태 유지로 강제 종료하거나 alias를 재전송·수동 전환하는 기능은 후속 작업이다.

V13의 product_search_index_activation_jobs는 UUID 기본값 uuidv7(), 대상·기대 버전에 대한 FK, 관리자별 멱등성 키 UNIQUE, 낙관적 락 버전을 사용한다. 활성화 멱등성 키는 적재 작업과 별도 공간이며, 완료 후에도 같은 관리자·키를 재사용하지 않는다. 같은 대상에 대한 재시도는 이전 작업이 전환 전 FAILED로 확정된 경우 새 키로 별도 작업을 만든다.

QUEUED/RUNNING/SWITCHING/UNCONFIRMED를 포함하는 부분 UNIQUE 인덱스로 전역 미완료 활성화 작업을 하나로 제한한다. 상태·시간·실패 정보의 비즈니스 규칙은 생성자와 전이 메서드에서 검증하며 DB CHECK로 중복하지 않는다. 적재 작업과의 상호 배제 및 인덱스 버전과의 동시 갱신은 공통 advisory lock과 짧은 DB 트랜잭션으로 구현한다.

V14는 활성화 작업에도 owner_token/lease_until과 만료 조회 인덱스를 추가한다. 활성화는 전용 실행·heartbeat 스레드를 사용하며 기존 PRODUCT_SEARCH_WORKER_ENABLED와 poll/lease/heartbeat 설정을 공유한다. RUNNING에서 lease가 만료되면 작업 FAILED/대상 READY로 종료하고, SWITCHING에서 만료되면 UNCONFIRMED/대상 ACTIVATING을 유지한다. 미완료 작업을 새 실행기에 재선점하거나 전환 요청을 재전송하지 않는다.

실행기는 전환 전에 매핑 버전·등록 해시의 현재 설정 호환성, 물리 인덱스 존재·읽기 가능 여부, 실제 alias의 대상·필터·routing을 확인한다. 기존 대상 제거(must_exist=true)와 새 대상 추가(is_write_index=false)는 하나의 Manage Aliases 요청으로 수행한다. 초기 활성화는 추가만 수행한다. 전환 요청의 자동 HTTP 재시도를 비활성화했으며, 응답 확인 후 alias가 새 인덱스 하나만 가리키는지 다시 조회한다. 전체 적재 검증을 다시 실행하는 기능은 아니다.

확인 후 이전 ACTIVE를 INACTIVE로 먼저 flush하고 새 버전을 ACTIVE로 바꾸며 작업 SUCCEEDED를 같은 트랜잭션에서 기록한다. 커밋 실패 시 두 버전과 작업 상태 모두 롤백한다. 이후 실패 기록이 가능하면 UNCONFIRMED로 남기고, DB 사용 불가 등으로 기록하지 못하면 SWITCHING을 유지해 lease 만료 처리에 맡긴다. 전환 전 실패만 FAILED/READY로 되돌려 새 키로 재시도할 수 있다.

이 흐름은 DB와 OpenSearch 사이의 원자적 트랜잭션이 아니다. 소유권 상실 전에 전송된 요청은 나중에 완료될 수 있으므로 미확정 전환은 계속 후속 적재·활성화를 막는다. alias를 외부에서 직접 변경하거나 관리 alias 설정을 작업 중 바꾸는 운영은 지원하지 않는다. 외부 변경을 완전히 차단하는 OpenSearch 차원의 비교·교환 보장은 없으며, 발견한 불일치는 요청 거부 또는 UNCONFIRMED로 처리한다. 미확정 결과의 강제 해소와 INACTIVE 재활성화는 후속 작업이다.

Alias 요청의 원자성은 [OpenSearch Manage Aliases API](https://docs.opensearch.org/latest/api-reference/alias/aliases-api/)를 따른다.

## 활성화 조정: 관측과 성공 확정

ProductSearchIndexAliasState는 OpenSearch 타입을 포함하지 않는 alias 스냅샷이다. matches()는 등록·전환 전후 검증에 공통으로 사용하며, classify()는 대상·이전 인덱스명에 따라 관측 결과를 분류한다. plain=false는 필터·routing 등 허용하지 않는 alias 설정이나 이름 충돌을 뜻한다. alias 조회 실패는 빈 인덱스 목록으로 표현하지 않고 LOOKUP_FAILED로 구분한다.

| 관측 결과 | 의미 | 조정 처리 |
|---|---|---|
| TARGET_CONFIRMED | 대상 인덱스 하나만 가리키며 alias 설정 정상 | 성공 확정 가능 |
| PREVIOUS_OBSERVED | 이전 인덱스 하나를 가리킴 | UNCONFIRMED 유지 |
| ALIAS_ABSENT | alias 없음 | UNCONFIRMED 유지 |
| MULTIPLE_INDICES | 여러 인덱스를 가리킴 | 유지, 운영자 확인 로그 |
| UNEXPECTED_INDEX | 대상·이전 버전 이외의 인덱스를 가리킴 | 유지, 운영자 확인 로그 |
| ALIAS_OPTIONS_MISMATCH | alias 필터·routing 또는 이름 충돌 | 유지, 운영자 확인 로그 |
| LOOKUP_FAILED | OpenSearch 조회 실패 | 유지, 안전한 실패 정보와 내부 예외 로그 |

이전 인덱스나 alias 부재를 반복 관측하더라도 전환 실패로 확정하지 않는다. 지연 중인 원래 전환 요청이 이후 적용될 수 있기 때문이다. 조정기는 alias 조회만 수행하며 생성·수정·전환 요청은 보내지 않는다. 대상 인덱스의 검색 품질·문서 전체를 다시 검증하는 기능도 포함하지 않는다. 성공 확정은 alias의 단독 관리 및 원래 전환 요청을 한 번만 전송한다는 기존 운영 조건을 전제로 한다.

조정 전에 만료된 활성화 실행 상태를 기존 복구 로직으로 정리한다. 유효한 RUNNING/SWITCHING lease에는 개입하지 않으며, 소유자가 없는 UNCONFIRMED 작업만 선점한다. 조정 시도 UUID와 lease는 활성화 실행기의 ownerToken과 별개다. 선점 시 활성화 작업의 낙관적 락 버전과 대상·이전 인덱스명을 스냅샷으로 기록한다. OpenSearch 조회는 외부 트랜잭션까지 중단한 상태에서 수행한다.

결과 반영 시 공통 관리 락과 작업·조정 행 락을 잡고 조정 lease, 활성화 상태·락 버전·소유자, 대상 이름·ACTIVATING 상태와 DB의 기대 활성 버전을 재검사한다. 변경되었다면 STALE 이력만 기록한다. TARGET_CONFIRMED일 때 기존 활성화 성공 처리와 공통 완료 로직을 사용해 이전 INACTIVE·대상 ACTIVE·작업 SUCCEEDED·조정 이력을 한 트랜잭션으로 갱신한다. DB 실패 시 모두 롤백한다. 미완료 조정 lease가 만료되면 EXPIRED 이력으로 종료하며 뒤늦은 관측 결과는 반영하지 않는다.

V15의 product_search_index_reconciliation_attempts는 시도별 UUID(DEFAULT uuidv7()), 작업 FK, 자동/수동 요청자(null=자동), 상태·생성/lease/완료 시각·관측 결과·인덱스 목록(JSONB)·alias 설정 정상 여부·안전한 실패 정보를 보관한다. 상태는 RUNNING/SUCCEEDED/UNRESOLVED/FAILED/STALE/EXPIRED다. RUNNING은 아직 관측 결과가 없고, FAILED는 조회 실패이며 활성화 작업 자체의 FAILED와 다르다. job_id별 finished_at IS NULL 부분 UNIQUE로 동시에 한 조정만 허용한다. 비즈니스 검증은 모델에서 수행하며 DB CHECK는 추가하지 않는다.

### POST /index-activations/{jobId}/reconcile

ADMIN 전용이며 요청 body나 Idempotency-Key는 필요 없다. JWT sub를 시도 요청자로 기록한다. 각 호출은 별도 확인 시도를 만들며, 자동 확인의 재시도 간격을 기다리지 않고 즉시 조회한다. 동시에 다른 조정이 실행 중이면 409 INDEX_RECONCILIATION_IN_PROGRESS다. 없는 작업은 404 INDEX_ACTIVATION_NOT_FOUND, 완료된 작업 또는 유효한 활성화 실행 중 작업은 409 INDEX_ACTIVATION_NOT_UNCONFIRMED다. 조정 중 lease가 만료되면 409 INDEX_RECONCILIATION_LEASE_EXPIRED이며 기존 작업은 유지한다.

200 응답은 {id,jobId,trigger,requestedBy,status,createdAt,finishedAt,observation,observedIndices,aliasPlain,failure}다. trigger는 AUTOMATIC/MANUAL이며, 관측 실패 시 observedIndices와 aliasPlain은 null이다. 200은 확인 시도의 완료를 뜻하며 성공 확정 여부는 status로 판단한다. OpenSearch 조회 실패도 FAILED 시도 이력과 안전한 failure를 반환한다. DB 저장 실패는 기존 503 INDEX_MANAGEMENT_UNAVAILABLE 계약을 사용한다. 자동 조정을 꺼도 수동 API는 유지한다.

GET /index-activations/{jobId}의 latestReconciliation은 같은 구조의 최근 조정 시도 또는 null이다. RUNNING/EXPIRED 시도에는 관측 정보가 없을 수 있다. 전체 이력은 DB에 보관하며 별도의 이력 목록 API는 이번 범위에 포함하지 않는다. 실행기나 조정기의 소유권·lease는 HTTP에 노출하지 않는다.

### 자동 확인 설정

product-service.search.reconciliation.enabled 기본 true이며 PRODUCT_SEARCH_RECONCILIATION_ENABLED로 제어한다. 자동 확인은 search.enabled와 worker.enabled도 true일 때 전용 스레드에서 실행한다. poll-interval-millis 기본 30000, retry-delay-millis 기본 60000, lease-millis 기본 60000이며 각각 PRODUCT_SEARCH_RECONCILIATION_POLL_INTERVAL_MILLIS / PRODUCT_SEARCH_RECONCILIATION_RETRY_DELAY_MILLIS / PRODUCT_SEARCH_RECONCILIATION_LEASE_MILLIS로 지정한다. 최근 시도가 끝난 뒤 retry-delay 동안 자동 재확인하지 않는다. 확인이 lease 안에 끝나지 않으면 결과를 폐기하고 만료 이력을 남긴다.

## 오류 계약

기존 ErrorDTO의 code/message를 사용한다.

| HTTP | code | 조건 |
|---|---|---|
| 400 | INVALID_INDEX_MANAGEMENT_REQUEST | body/header/query/path 검증 실패 |
| 401 / 403 | 기존 인증/인가 규칙 | 미인증/ADMIN 권한 없음 |
| 404 | INDEX_BUILD_NOT_FOUND / INDEX_VERSION_NOT_FOUND / INDEX_ACTIVATION_NOT_FOUND | 작업/버전 없음 |
| 409 | INDEX_BUILD_IN_PROGRESS | 다른 적재 작업 진행 중 |
| 409 | IDEMPOTENCY_KEY_CONFLICT | 같은 키에 다른 요청 |
| 409 | INDEX_NOT_READY | 활성화할 수 없는 상태 |
| 409 | ACTIVE_INDEX_CHANGED | 기대 활성 버전 불일치 |
| 409 | INDEX_STATE_UNCONFIRMED | alias와 DB 상태 조정 필요 |
| 409 | INDEX_ACTIVATION_IN_PROGRESS | 다른 활성화 진행 중 |
| 409 | INDEX_ACTIVATION_NOT_UNCONFIRMED | 조정 가능한 미확정 작업이 아님 |
| 409 | INDEX_RECONCILIATION_IN_PROGRESS | 해당 작업을 다른 조정기가 확인 중 |
| 409 | INDEX_RECONCILIATION_LEASE_EXPIRED | 관측 결과 반영 전에 조정 소유권 만료 |
| 503 | INDEX_MANAGEMENT_UNAVAILABLE | DB/실행기 등 관리 기능 사용 불가 |

OpenSearch 작업 중 발생하는 오류는 이미 202로 수락한 요청의 HTTP 결과를 바꾸지 않고 작업 상태에 기록한다. 작업 실패와 API 통신 실패를 구분한다.

## 실행기와 lease

V12 migration은 적재 작업에 owner_token/lease_until과 만료 조회 인덱스를 추가한다. 소유권 메타데이터는 HTTP DTO에 노출하지 않는다. 등록·선점은 공통 advisory lock 키를 사용하며 작업 행을 잠근 뒤 RUNNING/BUILDING 상태와 sourceProductCount, DB에서 발급한 소유 토큰·lease를 한 트랜잭션에 기록한다.

실행은 전용 단일 worker thread에서 수행하고 heartbeat는 별도 스레드에서 갱신한다. @Scheduled는 작업을 제출하고 즉시 반환하므로 기존 재고 만료 스케줄러를 적재 동안 막지 않는다. DB 작업 선점·갱신은 REQUIRES_NEW의 짧은 트랜잭션이며 OpenSearch 생성/전송/검증 중에는 DB 트랜잭션을 유지하지 않는다.

- worker.enabled 기본 true, poll-interval-millis 2000, lease-millis 60000, heartbeat-interval-millis 10000. lease는 heartbeat 간격의 두 배보다 커야 한다. PRODUCT_SEARCH_WORKER_ENABLED=false 또는 PRODUCT_SEARCH_ENABLED=false이면 자동 실행하지 않는다.
- 등록·lease 시각은 DB clock_timestamp()를 사용한다. 서버 간 애플리케이션 시계 차이에 의존하지 않는다.
- 생성 전에 현재 매핑 버전과 SHA-256이 등록 당시와 일치하는지 검사한다. 기존 물리 인덱스를 덮어쓰거나 삭제하지 않는다.
- Bulk 시도 전(재시도 포함), 다음 배치 조회 전, 진행률 기록 전 소유권을 확인한다. 성공한 Bulk 배치만 진행률에 반영한다.
- VERIFYING 전환 후 refresh/전체 문서 수, 공개 상품 수·가격 범위, 표본 문서와 카테고리·브랜드·가격 필터(공개 검색과 동일한 조건에 표본 ID를 추가해 해당 상품이 정확히 1건 검색되는지 확인), 분석 가능한 설명의 토큰 검색을 확인한다. 최종 DB 상품 수도 다시 비교한 후 SUCCEEDED/READY를 함께 기록한다. alias는 변경하지 않는다.
- 생성·적재·검증 오류는 작업 FAILED와 인덱스 FAILED를 함께 기록한다. 안전한 failureCode/failureMessage만 공개하고 내부 실패 로그에는 jobId/indexVersionId/물리 인덱스명, 실행 단계(CREATE_INDEX/LOAD_PRODUCTS/BEGIN_VERIFICATION/VERIFY_INDEX/COMPLETE_BUILD), 예외 타입과 원인 체인을 포함한 스택 트레이스를 남긴다. heartbeat와 실패 상태 기록 오류는 HEARTBEAT/RECORD_FAILURE로 구분한다. 초기 적재 로그에는 완료 배치 수·적재 수·커서가, Bulk 오류 로그에는 시도 횟수·문서 수 또는 상품 ID·HTTP 상태·오류 타입이 포함된다. Bulk item의 reason이나 상품 문서 전체는 별도로 출력하지 않는다.
- 만료된 RUNNING/VERIFYING은 행 잠금으로 선점해 INTERRUPTED/FAILED로 종료한다. lease 없는 기존 실행 상태도 중단 대상으로 본다. 같은 인덱스를 재선점하지 않으며 재시도는 새 버전·작업으로 한다.
- 만료·잘못된 소유 토큰·terminal 상태의 실행기는 heartbeat와 진행률/성공/실패 갱신이 거부된다. heartbeat 오류가 나면 새 요청을 중단하고, 갱신되지 않는 lease가 만료된 뒤 다른 poll이 중단을 기록한다.
- 이미 전송한 OpenSearch 요청은 소유권 상실 이후에도 완료될 수 있다. DB lease와 OpenSearch 쓰기는 원자적이지 않다. 따라서 중단 인덱스는 READY로 승격하거나 같은 인덱스에 새 실행기를 투입하지 않는다. 해당 인덱스의 정리는 별도 관리 작업이다.

상품·카테고리 쓰기 중단은 여전히 운영 전제다. 같은 문서 수를 유지하는 수정까지 탐지하거나 온라인 변경을 따라잡는 동기화 기능은 포함하지 않는다.
