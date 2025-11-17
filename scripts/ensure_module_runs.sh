#!/usr/bin/env bash

set -euo pipefail

QUERY="update global_property set property_value='true' where property like '%started%';"
MYSQL_USER="root"
MYSQL_PASSWORD="Admin123"
MYSQL_DATABASE="refapp3"

container_id="$(docker ps --format '{{.ID}} {{.Image}} {{.Names}}' | awk '/mysql/ {print $1; exit}')"

if [[ -z "${container_id}" ]]; then
  echo "Unable to locate a running MySQL container (no container name or image containing 'mysql')." >&2
  exit 1
fi

docker exec -i "${container_id}" mysql \
  "-u${MYSQL_USER}" "-p${MYSQL_PASSWORD}" "${MYSQL_DATABASE}" \
  -e "${QUERY}"

echo "Global properties matching '%started%' updated to 'true'."

