#!/bin/sh
set -eu

: "${JWT_SECRET:?JWT_SECRET é obrigatório}"
: "${INTERNAL_API_KEY:?INTERNAL_API_KEY é obrigatório}"
export CONSUL_HTTP_ADDR="${CONSUL_HTTP_ADDR:-http://consul:8500}"

until consul members >/dev/null 2>&1; do
  sleep 1
done

sed -e "s|__JWT_SECRET__|$JWT_SECRET|g" \
    -e "s|__INTERNAL_API_KEY__|$INTERNAL_API_KEY|g" \
    /seed/application.yml > /tmp/application.yml
consul kv put config/application/data @/tmp/application.yml
consul kv put config/application,docker/data @/tmp/application.yml

for file in /seed/services/*.yml; do
  [ -f "$file" ] || continue
  name=$(basename "$file" .yml)
  consul kv put "config/$name/data" "@$file"
  consul kv put "config/$name,docker/data" "@$file"
done