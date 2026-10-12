# 어드민 상품 검색 인덱스 관리 계약

## 이번 단계의 범위

상태 모델·스키마·DTO에 더해 적재 요청 등록(POST /index-builds), 작업 조회(GET /index-builds/{jobId}), 버전 목록(GET /index-versions)을 controller → application service → PostgreSQL adapter로 연결한다. 작업 claim·실행기, 로더 진행률 반영, OpenSearch 인덱스 생성·검증·alias 전환·복구는 후속이다. 활성화 라우트는 아직 구현하지 않는다. 등록된 작업은 실행기를 연결하기 전까지 QUEUED에 머문다.

첫 버전은 적재 시작부터 검증과 활성화 완료까지 상품·카테고리 쓰기가 멈춰 있어야 한다. catalogWritesPaused=true는 관리자의 확인이며 실제 쓰기 차단 장치를 대신하지 않는다. 변경 이벤트 동기화와 온라인 재색인 조정은 후속이다.

## 모델과 상태

두 모델과 상태 enum은 product-domain-application의 `application.searchindex.model`에 둔다. 검색 인덱스 운영 생명주기는 상품 domain-core의 비즈니스 애그리거트와 분리한다. JPA 엔티티와 repository는 product-dataaccess에 유지하며 모델에는 Spring/JPA/OpenSearch 타입을 사용하지 않는다.

### 인덱스 버전

`ProductSearchIndexVersion`은 UUID id와 단조 증가하는 versionNumber를 분리한다. versionNumber는 PostgreSQL sequence에서 발급하고 실제 이름은 서버가 products-v{versionNumber}로 생성한다. 순번에 빈 번호가 생길 수 있다. 요청이 실제 인덱스 이름/매핑 내용을 지정하지 않는다.

mappingVersion은 저장소에서 관리하는 매핑 정의의 버전이며 mappingHash는 사용한 매핑 JSON 파일 바이트의 SHA-256 소문자 hex(64자)다. 같은 매핑으로 여러 물리 인덱스를 만들 수 있다. requestedBy는 JWT의 sub이며 요청 body에서 받지 않는다. 기존 인증 converter의 principal name은 preferred_username이므로 Authentication.getName() 대신 JWT subject를 직접 사용한다.

| 상태 | 의미 | 다음 상태 |
|---|---|---|
| REGISTERED | DB에 버전 등록, OpenSearch 생성 전 | BUILDING, FAILED |
| BUILDING | 인덱스 생성/적재/검증 중 | READY, FAILED |
| READY | 적재와 검증 완료, 공개 검색 연결 전 | ACTIVATING |
| ACTIVATING | 활성화 의도 기록, 실제 alias 결과 확인 중 | ACTIVE |
| ACTIVE | products-read가 이 인덱스 하나를 가리킨다고 확인 | INACTIVE |
| INACTIVE | 과거 검증된 활성 버전, 재활성화 후보 | ACTIVATING |
| FAILED | 생성/적재/검증 실패 | 없음 |

OpenSearch 호출 타임아웃으로 alias 결과가 불명확하면 ACTIVATING을 유지한다. 임의로 ACTIVE/FAILED로 바꾸지 않고 실제 alias 확인·조정으로 해소해야 한다. 재활성화 후보의 존재·매핑 호환성과 데이터 시점은 활성화 구현에서 다시 검증한다. 기존 ACTIVE를 INACTIVE로 바꾸고 새 버전을 ACTIVE로 바꾸는 DB 기록은 한 트랜잭션에서 수행한다.

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

READY/INACTIVE만 요청 가능하며 진행 중 적재 작업이 있으면 거부한다. 동일 활성화 요청의 멱등성은 적재 요청과 별도로 관리해야 한다. 활성화 요청 키·기대 버전·관리자·실행 의도를 영속적으로 기록하는 실행 모델은 다음 단계에서 추가한다.

응답 202는 활성화 의도를 수락했다는 의미다. 예: {"indexVersionId":"01940000-0000-7000-8000-000000000002","expectedActiveVersionId":null,"status":"ACTIVATING"}. ACTIVE 확인은 버전 목록 조회로 한다. 별도 작업 없이 DB 상태만 ACTIVE로 변경하는 API가 아니다.

## 오류 계약

기존 ErrorDTO의 code/message를 사용한다.

| HTTP | code | 조건 |
|---|---|---|
| 400 | INVALID_INDEX_MANAGEMENT_REQUEST | body/header/query/path 검증 실패 |
| 401 / 403 | 기존 인증/인가 규칙 | 미인증/ADMIN 권한 없음 |
| 404 | INDEX_BUILD_NOT_FOUND / INDEX_VERSION_NOT_FOUND | 작업/버전 없음 |
| 409 | INDEX_BUILD_IN_PROGRESS | 다른 적재 작업 진행 중 |
| 409 | IDEMPOTENCY_KEY_CONFLICT | 같은 키에 다른 요청 |
| 409 | INDEX_NOT_READY | 활성화할 수 없는 상태 |
| 409 | ACTIVE_INDEX_CHANGED | 기대 활성 버전 불일치 |
| 409 | INDEX_STATE_UNCONFIRMED | alias와 DB 상태 조정 필요 |
| 409 | INDEX_ACTIVATION_IN_PROGRESS | 다른 활성화 진행 중 |
| 503 | INDEX_MANAGEMENT_UNAVAILABLE | DB/실행기 등 관리 기능 사용 불가 |

OpenSearch 작업 중 발생하는 오류는 이미 202로 수락한 요청의 HTTP 결과를 바꾸지 않고 작업 상태에 기록한다. 작업 실패와 API 통신 실패를 구분한다.
