#!/usr/bin/env bash
set -euo pipefail

# The same file doubles as a fake Docker executable via a temporary symlink.
case "${0##*/}" in
    nc) echo "imok"; exit 0 ;;
    kcat) printf ' 3 brokers:\n product.catalog.changed\n'; exit 0 ;;
esac
if [[ "${0##*/}" == "docker" ]]; then
    case "$1" in
        compose)
            [[ "${POSTGRES_TEST_SCENARIO}" == "compose_failure" ]] && exit 1
            [[ "${POSTGRES_TEST_SCENARIO}" == "missing" ]] || echo "postgres-test-container"
            ;;
        inspect)
            case "${POSTGRES_TEST_SCENARIO}" in
                exited|restarting) echo "${POSTGRES_TEST_SCENARIO}" ;;
                inspect_failure) exit 1 ;;
                *) echo "running" ;;
            esac
            ;;
        exec)
            # A missing TCP host would incorrectly accept the temporary initdb server.
            [[ "$*" == *'pg_isready -h 127.0.0.1'* ]] || exit 2
            case "${POSTGRES_TEST_SCENARIO}" in
                ready) exit 0 ;;
                initializing)
                    if [[ -f "${POSTGRES_TEST_DIRECTORY}/probed" ]]; then
                        exit 0
                    fi
                    touch "${POSTGRES_TEST_DIRECTORY}/probed"
                    exit 1
                    ;;
                *) exit 1 ;;
            esac
            ;;
        logs) echo "PostgreSQL diagnostic log fixture" ;;
        *) echo "Unexpected Docker call: $*" >&2; exit 2 ;;
    esac
    exit 0
fi

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
postgres_test_directory=$(mktemp -d)
export POSTGRES_TEST_DIRECTORY="${postgres_test_directory}"
trap 'for executable in docker nc kcat; do unlink "${postgres_test_directory}/${executable}"; done; rmdir "${postgres_test_directory}"' EXIT
for executable in docker nc kcat; do
    ln -s "${TEST_DIR}/PostgresReadinessTest.sh" "${postgres_test_directory}/${executable}"
done
export PATH="${postgres_test_directory}:${PATH}"

check_case() {
    local scenario="$1"
    local expected_status="$2"
    local expected_message="$3"
    local timeout="${4:-1}"
    local output
    local status=0
    output=$(POSTGRES_TEST_SCENARIO="${scenario}" POSTGRES_STARTUP_TIMEOUT_SECONDS="${timeout}" \
        bash "${TEST_DIR}/../wait-postgres.sh" 2>&1) || status=$?
    if [[ "${status}" != "${expected_status}" || "${output}" != *"${expected_message}"* ]]; then
        echo "FAIL ${scenario}: expected exit ${expected_status} and '${expected_message}', got ${status}" >&2
        echo "${output}" >&2
        exit 1
    fi
    echo "PASS ${scenario}"
}

check_case ready 0 "accepting TCP connections"
check_case initializing 0 "accepting TCP connections" 5
unlink "${postgres_test_directory}/probed"
check_case exited 1 "PostgreSQL diagnostic log fixture"
check_case restarting 1 "container is restarting"
check_case timeout 1 "Timed out waiting for PostgreSQL TCP readiness"
check_case missing 1 "container was not created"
check_case inspect_failure 1 "Could not inspect PostgreSQL container"
check_case compose_failure 1 "Could not resolve the PostgreSQL container"
check_case invalid_timeout 1 "must be a positive integer" invalid

# Verify startup.sh propagates the DB failure instead of starting Connect or declaring success.
startup_status=0
startup_output=$(POSTGRES_TEST_SCENARIO=exited bash "${TEST_DIR}/../../startup.sh" 2>&1) || startup_status=$?
if [[ "${startup_status}" != "1" || "${startup_output}" != *"container is exited"* \
        || "${startup_output}" == *"Preparing Kafka Connect"* \
        || "${startup_output}" == *"Our services are up"* ]]; then
    echo "FAIL startup must stop after PostgreSQL failure" >&2
    echo "${startup_output}" >&2
    exit 1
fi
echo "PASS startup stops before Kafka Connect when PostgreSQL fails"
