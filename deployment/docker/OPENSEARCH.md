# 로컬 OpenSearch

OpenSearch 3.9.0 단일 노드와 `analysis-nori` 플러그인을 사용한다. 플러그인은 Docker 이미지 빌드 시 설치한다. 버전 변경 시 Dockerfile의 base image와 Compose image 태그를 함께 변경한다.

## 실행

워크스페이스 루트에서:

```bash
make opensearch-up
```

전체 인프라를 실행하는 `make infra-up` / `make up`에도 포함된다. Compose는 클러스터 상태가 yellow 이상인 것을 확인한 후 다음 단계로 진행한다. 단일 노드는 replica 때문에 yellow일 수 있으므로 green을 필수로 요구하지 않는다. 처음 실행할 때 이미지/플러그인 다운로드가 필요하다.

Docker 디렉터리에서 직접 실행하려면:

```bash
bash scripts/start-opensearch.sh
```

상품 앱은 기본 설정에서 시작 시 최초 인덱스 생성·DB 상품 적재·alias 활성화를 자동 실행한다. ACTIVE 인덱스가 있으면 건너뛰며 초기화 실패 시 앱 시작도 실패한다. 앱을 시작하기 전에 OpenSearch를 실행한다. 수동 관리만 사용하려면 백엔드 환경 변수 `PRODUCT_SEARCH_INITIAL_LOAD_ENABLED=false`로 끈다. 상세 설정은 [인덱스 관리 문서](../../product-service/docs/ADMIN_PRODUCT_SEARCH_INDEX_MANAGEMENT.md#앱-시작-시-초기-적재)를 참고한다.

## 연결과 설정

| 항목 | 기본값 |
|---|---|
| 호스트에서 실행하는 Java 서비스 | `http://localhost:9200` |
| 같은 Compose 네트워크의 컨테이너 | `http://eco-opensearch:9200` |
| 호스트 바인딩 | `127.0.0.1` |
| 인증/TLS | 로컬 개발용으로 비활성화 |
| JVM heap | 최소/최대 512 MB |
| 데이터 | `./volumes/opensearch/data` → `/usr/share/opensearch/data` bind mount |

다음 값은 shell 환경 변수 또는 `deployment/docker/.env`에서 설정할 수 있다. 백엔드의 `ecommerce-msa/.env`는 이 Compose 기동에서 읽지 않는다.

| 변수 | 기본값 | 용도 |
|---|---|---|
| `OPENSEARCH_PORT` | `9200` | 호스트 포트; 컨테이너 내부는 항상 9200 |
| `OPENSEARCH_HEAP_SIZE` | `512m` | JVM 최소/최대 heap에 같은 값 적용 |
| `OPENSEARCH_STARTUP_TIMEOUT_SECONDS` | `180` | 컨테이너 health 대기 제한, 양의 정수. shell 환경 변수로 지정 |
| `GLOBAL_NETWORK` | `ecomart` | 기존 공통 Compose 네트워크 |

```bash
OPENSEARCH_PORT=19200 OPENSEARCH_HEAP_SIZE=1g OPENSEARCH_STARTUP_TIMEOUT_SECONDS=240 make opensearch-up
```

OpenSearch 컨테이너는 인증이 없으므로 호스트 포트는 loopback에만 공개한다. 이 설정은 로컬 개발용이다. Docker VM에는 heap 외의 메모리도 필요하므로 Kafka 등 기존 인프라와 함께 실행할 여유 메모리를 확보한다. Linux/Docker VM에서 `vm.max_map_count`는 최소 262144가 필요하다. bootstrap 오류가 발생하면 아래 로그와 [공식 Docker 설치 문서](https://docs.opensearch.org/latest/install-and-configure/install-opensearch/docker/)의 호스트 설정을 확인한다.

## 확인

기본 호스트 포트 기준:

```bash
curl --fail http://localhost:9200
curl --fail 'http://localhost:9200/_cluster/health?pretty'
curl --fail 'http://localhost:9200/_cat/plugins?v'
curl --fail -H 'Content-Type: application/json' -X POST \
  http://localhost:9200/_analyze \
  -d '{"tokenizer":"nori_tokenizer","text":"무선 이어폰"}'
```

Docker 디렉터리에서 상태/로그 확인:

```bash
docker compose -f common.yml -f opensearch.yml ps
docker compose -f common.yml -f opensearch.yml logs --tail 100 eco-opensearch
```

## 중지와 초기화

- `make down` / `make infra-stop`: 컨테이너 중지, 인덱스 유지.
- `make infra-reset`: 다른 기존 인프라와 함께 OpenSearch 컨테이너를 제거하고 `volumes/opensearch/data`의 내용 삭제. 재색인이 필요하다.
- OpenSearch만 중지하려면 Docker 디렉터리에서 `docker compose -f common.yml -f opensearch.yml stop eco-opensearch`를 실행한다.

데이터 디렉터리는 최초 Compose 기동 시 생성된다. Linux에서 쓰기 권한 오류가 발생하면 이미지의 OpenSearch 사용자(UID/GID 1000)가 해당 디렉터리에 쓸 수 있도록 호스트 소유권/권한을 설정한다. 이전 named volume은 자동 이동/삭제하지 않으며, 기존 데이터를 유지하려면 새 경로로 옮긴 뒤 기동한다.

인프라 기동은 상품 인덱스를 자동 생성/적재하지 않는다. 검색 어댑터는 구현되어 있으며, [매핑/alias 준비와 연결 설정](../../product-service/docs/STOREFRONT_PRODUCT_SEARCH_OPENSEARCH.md)을 참고한다. DB 초기 적재와 outbox 인덱싱은 후속 작업이다. 인덱스가 없으면 상품 검색 API는 `503 SEARCH_UNAVAILABLE`을 반환한다.

참고: [공식 플러그인 설치 문서](https://docs.opensearch.org/latest/install-and-configure/plugins/).
