#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/.."

postgres_timeout="${POSTGRES_STARTUP_TIMEOUT_SECONDS:-120}"
if [[ ! "${postgres_timeout}" =~ ^[1-9][0-9]*$ ]]; then
    echo "POSTGRES_STARTUP_TIMEOUT_SECONDS must be a positive integer." >&2
    exit 1
fi

if ! postgres_container=$(docker compose -f common.yml -f backing_services.yml ps --all --quiet eco-postgres); then
    echo "Could not resolve the PostgreSQL container. Check Docker Compose status." >&2
    exit 1
fi
if [[ -z "${postgres_container}" ]]; then
    echo "PostgreSQL container was not created; refusing to continue startup." >&2
    exit 1
fi

postgres_fail() {
    echo "$1" >&2
    echo "Recent PostgreSQL container logs:" >&2
    docker logs --tail 50 "${postgres_container}" >&2 || true
    exit 1
}

echo "Waiting for PostgreSQL TCP readiness (timeout: ${postgres_timeout}s)..."
postgres_wait_started=${SECONDS}
while true; do
    if ! postgres_state=$(docker inspect --format '{{.State.Status}}' "${postgres_container}"); then
        postgres_fail "Could not inspect PostgreSQL container; refusing to continue startup."
    fi
    case "${postgres_state}" in
        exited|dead|restarting|paused|removing)
            postgres_fail "PostgreSQL container is ${postgres_state}; refusing to continue startup."
            ;;
    esac

    # The image's temporary initdb server only listens on a Unix socket.
    # Require TCP so init.sql and the temporary-server shutdown have completed.
    if [[ "${postgres_state}" == "running" ]] && docker exec "${postgres_container}" sh -c \
        'pg_isready -h 127.0.0.1 -p 5432 -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-${POSTGRES_USER:-postgres}}" -t 2' \
        >/dev/null 2>&1; then
        echo "PostgreSQL is accepting TCP connections."
        exit 0
    fi

    if (( SECONDS - postgres_wait_started >= postgres_timeout )); then
        postgres_fail "Timed out waiting for PostgreSQL TCP readiness after ${postgres_timeout}s."
    fi
    sleep 1
done
