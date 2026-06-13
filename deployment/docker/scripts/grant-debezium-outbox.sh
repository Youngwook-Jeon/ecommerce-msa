#!/usr/bin/env bash
set -euo pipefail

PG_CONTAINER="${PG_CONTAINER:-eco-postgres}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-user}"
PGPASSWORD="${PGPASSWORD:-password}"
PGDATABASE="${PGDATABASE:-ecodb_product}"
# auto: use Docker when eco-postgres is running, else fall back to host psql
PG_USE_DOCKER="${PG_USE_DOCKER:-auto}"

use_docker_psql() {
  if [[ "${PG_USE_DOCKER}" == "false" ]]; then
    return 1
  fi
  if ! command -v docker >/dev/null 2>&1; then
    return 1
  fi
  if ! docker ps --format '{{.Names}}' | grep -qx "${PG_CONTAINER}"; then
    if [[ "${PG_USE_DOCKER}" == "true" ]]; then
      echo "PostgreSQL container '${PG_CONTAINER}' is not running." >&2
      echo "Start infra first: cd deployment/docker && ./startup.sh" >&2
      exit 1
    fi
    return 1
  fi
  return 0
}

run_psql() {
  if use_docker_psql; then
    echo "Running psql inside Docker container '${PG_CONTAINER}'..."
    docker exec -i -e PGPASSWORD="${PGPASSWORD}" "${PG_CONTAINER}" \
      psql -U "${PGUSER}" -d "${PGDATABASE}" -v ON_ERROR_STOP=1
    return
  fi

  if command -v psql >/dev/null 2>&1; then
    echo "Running host psql against ${PGHOST}:${PGPORT}..."
    export PGPASSWORD
    psql -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -v ON_ERROR_STOP=1
    return
  fi

  echo "psql not found on host and Docker container '${PG_CONTAINER}' is not running." >&2
  echo "Start PostgreSQL: cd deployment/docker && ./startup.sh" >&2
  echo "Or install psql locally and retry." >&2
  exit 1
}

run_psql <<'SQL'
DO $$
BEGIN
  IF to_regclass('product.product_catalog_outbox') IS NULL THEN
    RAISE EXCEPTION 'Table product.product_catalog_outbox does not exist. Run product-service Flyway migrations first.';
  END IF;
END $$;

GRANT USAGE ON SCHEMA product TO debezium;
GRANT SELECT ON TABLE product.product_catalog_outbox TO debezium;

-- Publication must exist before connector start (publication.autocreate.mode=disabled).
-- debezium role cannot CREATE PUBLICATION; admin user (POSTGRES_USER) creates it here.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'dbz_product_catalog_outbox_pub') THEN
    CREATE PUBLICATION dbz_product_catalog_outbox_pub FOR TABLE product.product_catalog_outbox;
  ELSIF NOT EXISTS (
    SELECT 1
    FROM pg_publication_tables
    WHERE pubname = 'dbz_product_catalog_outbox_pub'
      AND schemaname = 'product'
      AND tablename = 'product_catalog_outbox'
  ) THEN
    ALTER PUBLICATION dbz_product_catalog_outbox_pub ADD TABLE product.product_catalog_outbox;
  END IF;
END $$;
SQL

echo "Debezium grants and publication dbz_product_catalog_outbox_pub are ready"
