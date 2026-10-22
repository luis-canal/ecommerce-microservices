#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROOT_DIR"

if [ ! -f .env ]; then
  echo "Arquivo .env ausente. Copie .env.example para .env e ajuste as senhas e segredos." >&2
  exit 1
fi

./mvnw -DskipTests package

if [ "${1:-}" = "--build-only" ]; then
  exit 0
fi

if [ "$#" -eq 0 ]; then
  set -- up --build
fi

exec docker compose "$@"