#!/usr/bin/env bash
set -euo pipefail

# Invoked through a temporary symlink to replace curl without a running stack.
if [[ "${0##*/}" == "curl" ]]; then
    request_url=""
    for argument in "$@"; do
        [[ "${argument}" == http* ]] && request_url="${argument}"
    done
    if [[ "${request_url}" == */status ]]; then
        state="RUNNING"
        tasks='[{"id":0,"state":"RUNNING"}]'
        case "${CONNECTOR_TEST_SCENARIO}" in
            failed_task) tasks='[{"id":0,"state":"FAILED","trace":"Replication connection limit exceeded"}]' ;;
            failed_connector) state="FAILED" ;;
            mixed_tasks) tasks='[{"id":0,"state":"RUNNING"},{"id":1,"state":"FAILED"}]' ;;
            not_found|empty_tasks|starting)
                if [[ ! -f "${CONNECTOR_TEST_DIRECTORY}/polled" ]]; then
                    touch "${CONNECTOR_TEST_DIRECTORY}/polled"
                    case "${CONNECTOR_TEST_SCENARIO}" in
                        not_found) printf '{}\n404'; exit 0 ;;
                        empty_tasks) tasks='[]' ;;
                        starting) state="UNASSIGNED" ;;
                    esac
                fi
                ;;
        esac
        printf '{"connector":{"state":"%s"},"tasks":%s}\n200' "${state}" "${tasks}"
    else
        echo '{}'
    fi
    exit 0
fi

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
connector_test_directory=$(mktemp -d)
export CONNECTOR_TEST_DIRECTORY="${connector_test_directory}"
trap 'unlink "${connector_test_directory}/curl"; [[ ! -f "${connector_test_directory}/polled" ]] || unlink "${connector_test_directory}/polled"; rmdir "${connector_test_directory}"' EXIT
ln -s "${TEST_DIR}/DebeziumConnectorStatusTest.sh" "${connector_test_directory}/curl"
export PATH="${connector_test_directory}:${PATH}"

check_case() {
    local scenario="$1" expected_status="$2" expected_message="$3"
    local output status=0
    [[ ! -f "${connector_test_directory}/polled" ]] || unlink "${connector_test_directory}/polled"
    output=$(CONNECTOR_TEST_SCENARIO="${scenario}" bash "${TEST_DIR}/../register-debezium-connectors.sh" 2>&1) || status=$?
    if [[ "${status}" != "${expected_status}" || "${output}" != *"${expected_message}"* ]]; then
        echo "FAIL ${scenario}: expected exit ${expected_status} and '${expected_message}', got ${status}" >&2
        echo "${output}" >&2
        exit 1
    fi
    if [[ "${expected_status}" != 0 && "${output}" == *"All Debezium connectors registered."* ]]; then
        echo "FAIL ${scenario}: failed CDC must not report successful registration" >&2
        exit 1
    fi
    echo "PASS ${scenario}"
}

check_case ready 0 "All Debezium connectors registered."
check_case failed_task 1 "Replication connection limit exceeded"
check_case failed_connector 1 "Debezium connector or task failed:"
check_case mixed_tasks 1 "Debezium connector or task failed:"
check_case not_found 0 "All Debezium connectors registered."
check_case empty_tasks 0 "All Debezium connectors registered."
check_case starting 0 "All Debezium connectors registered."

# Every configured PostgreSQL connector needs a slot and a WAL sender.
connector_count=$(jq -s '[.[] | select(.config."connector.class" == "io.debezium.connector.postgresql.PostgresConnector")] | length' \
    "${TEST_DIR}/../../connectors/"*-connector.json)
for setting in max_replication_slots max_wal_senders; do
    capacity=$(sed -n "s/.*${setting}=\([0-9]*\).*/\1/p" "${TEST_DIR}/../../backing_services.yml")
    if (( capacity < connector_count )); then
        echo "FAIL ${setting}: ${capacity} is insufficient for ${connector_count} PostgreSQL connectors" >&2
        exit 1
    fi
    echo "PASS ${setting} supports all ${connector_count} PostgreSQL connectors"
done
