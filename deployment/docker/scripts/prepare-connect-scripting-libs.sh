#!/usr/bin/env bash
# Downloads Debezium scripting + Groovy JARs for payment outbox Filter SMT.
# Mounted over /kafka/external_libs/debezium-scripting when ENABLE_DEBEZIUM_SCRIPTING=true.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIBS_DIR="${SCRIPT_DIR}/../connect-libs/debezium-scripting"
DEBEZIUM_VERSION="${DEBEZIUM_VERSION:-3.0.8.Final}"
GROOVY_VERSION="${GROOVY_VERSION:-4.0.24}"

mkdir -p "${LIBS_DIR}"

download() {
  local url="$1"
  local out="$2"
  if [[ -f "${out}" ]]; then
    echo "Already present: $(basename "${out}")"
    return
  fi
  echo "Downloading $(basename "${out}")..."
  curl -fsSL -o "${out}" "${url}"
}

MAVEN_CENTRAL="https://repo1.maven.org/maven2"

download \
  "${MAVEN_CENTRAL}/io/debezium/debezium-scripting/${DEBEZIUM_VERSION}/debezium-scripting-${DEBEZIUM_VERSION}.jar" \
  "${LIBS_DIR}/debezium-scripting-${DEBEZIUM_VERSION}.jar"

download \
  "${MAVEN_CENTRAL}/org/apache/groovy/groovy/${GROOVY_VERSION}/groovy-${GROOVY_VERSION}.jar" \
  "${LIBS_DIR}/groovy-${GROOVY_VERSION}.jar"

download \
  "${MAVEN_CENTRAL}/org/apache/groovy/groovy-jsr223/${GROOVY_VERSION}/groovy-jsr223-${GROOVY_VERSION}.jar" \
  "${LIBS_DIR}/groovy-jsr223-${GROOVY_VERSION}.jar"

download \
  "${MAVEN_CENTRAL}/org/apache/groovy/groovy-json/${GROOVY_VERSION}/groovy-json-${GROOVY_VERSION}.jar" \
  "${LIBS_DIR}/groovy-json-${GROOVY_VERSION}.jar"

echo "Connect scripting libs ready in ${LIBS_DIR}"
ls -la "${LIBS_DIR}"
